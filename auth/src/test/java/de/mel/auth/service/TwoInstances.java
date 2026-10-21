package de.mel.auth.service;

import fun.with.promise.Promise;

import de.mel.auth.MelStrings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.Service;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelSocket;
import de.mel.auth.socket.SocketState;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.Promises;
import de.mel.sql.Hash;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two Mel instances on loopback for the socket tests. Unlike in {@link RegistrationTest} they do not find each other by multicast
 * (each one sends its brotcasts to a port nobody listens on), so every connection between them is one the test made.
 * "server" and "client" only say who pairs with whom, both can open connections to the other.
 */
public class TwoInstances implements AutoCloseable {
    static final int TIMEOUT_SECONDS = 30;

    /**
     * accepts everyone at once, so pairing needs no user
     */
    static final IRegisterHandler ACCEPT_ALL = new SwitchableUser(true);

    /**
     * Plays the user: accepts or rejects within the call, as {@link #accept} says at that moment.
     */
    static class SwitchableUser implements IRegisterHandler {
        volatile boolean accept;

        SwitchableUser(boolean accept) {
            this.accept = accept;
        }

        @Override
        public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
            if (accept)
                listener.onCertificateAccepted(request, certificate);
            else
                listener.onCertificateRejected(request, certificate);
        }

        @Override
        public void onRegistrationCompleted(Certificate partnerCertificate) {
        }

        @Override
        public void onRemoteRejected(Certificate partnerCertificate) {
        }

        @Override
        public void onLocallyRejected(Certificate partnerCertificate) {
        }

        @Override
        public void onRemoteAccepted(Certificate partnerCertificate) {
        }

        @Override
        public void onLocallyAccepted(Certificate partnerCertificate) {
        }
    }

    final MelAuthService server;
    /**
     * Changes when the client is restarted, see {@link #restartClient()}
     */
    volatile MelAuthService client;
    private final Class<? extends Bootloader<? extends MelService>>[] bootloaders;

    /**
     * @param portBase takes 10 ports from here on
     */
    TwoInstances(File dir, int portBase, IRegisterHandler serverUser, IRegisterHandler clientUser) throws Exception {
        this(dir, portBase, serverUser, clientUser, new Class[0]);
    }

    /**
     * @param bootloaders the services both instances can run
     */
    @SafeVarargs
    TwoInstances(File dir, int portBase, IRegisterHandler serverUser, IRegisterHandler clientUser, Class<? extends Bootloader<? extends MelService>>... bootloaders) throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        this.bootloaders = bootloaders;
        server = boot(dir, "SERVER", portBase, portBase + 1, portBase + 4, portBase + 6, bootloaders);
        client = boot(dir, "CLIENT", portBase + 2, portBase + 3, portBase + 5, portBase + 7, bootloaders);
        server.addRegisterHandler(serverUser);
        client.addRegisterHandler(clientUser);
    }

    private static MelAuthService boot(File parent, String name, int port, int deliveryPort, int brotcastListenerPort, int brotcastPort, Class<? extends Bootloader<? extends MelService>>[] bootloaders) throws Exception {
        File dir = new File(parent, name);
        dir.mkdirs();
        MelAuthSettings settings = new MelAuthSettings().setPort(port).setDeliveryPort(deliveryPort)
                .setBrotcastListenerPort(brotcastListenerPort).setBrotcastPort(brotcastPort)
                .setWorkingDirectory(dir).setName(name).setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(dir, MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        return boot(settings, name, bootloaders);
    }

    private static MelAuthService boot(MelAuthSettings settings, String name, Class<? extends Bootloader<? extends MelService>>[] bootloaders) throws Exception {
        AtomicReference<MelAuthService> booted = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        new MelBoot(settings, new PowerManager(settings), bootloaders).boot().onDone(service -> {
            booted.set(service);
            latch.countDown();
        }).onError(e -> {
            failure.set(e);
            latch.countDown();
        });
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), name + " did not boot in time");
        assertNull(failure.get(), name + " failed to boot");
        return booted.get();
    }

    /**
     * Shuts the client down and starts it again, from what it left on disk: same settings, same ports, same pairing, same services.
     * What the app does when it is closed and opened again, or when it dies and is started again.
     */
    void restartClient() throws Exception {
        restartClient(() -> {
        });
    }

    /**
     * @param whileDown is run when the client is shut down and not started again yet
     */
    void restartClient(Runnable whileDown) throws Exception {
        MelAuthService old = client;
        MelAuthSettings settings = old.getSettings();
        CountLock lock = new CountLock().lock();
        Promises.allSettled(old.shutDown()).onDone(r -> lock.unlock());
        lock.lock();
        whileDown.run();
        client = boot(settings, "CLIENT", bootloaders);
        client.addRegisterHandler(ACCEPT_ALL);
    }

    /**
     * The client pairs with the server, both users have to accept.
     *
     * @return whether the client completed the pairing in time
     */
    boolean pairAgain() throws Exception {
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch connected = new CountDownLatch(1);
        client.addRegisteredHandler((service, cert) -> registered.countDown());
        // the connect promise is settled when the whole job is over: after a registration the client also connects, and that connection is part of pairing
        client.connect("localhost", server.getSettings().getPort(), server.getSettings().getDeliveryPort(), true)
                .onDone(a -> connected.countDown()).onError(e -> connected.countDown());
        if (!registered.await(10, TimeUnit.SECONDS))
            return false;
        assertTrue(connected.await(10, TimeUnit.SECONDS), "the client did not connect after the registration");
        return true;
    }

    /**
     * Pairs and waits until both sides trust each other.
     */
    void pair() throws Exception {
        assertTrue(pairAgain(), "pairing did not complete");
        assertTrue(waitFor(() -> trusts(server, client) && trusts(client, server)), "the two do not trust each other after pairing");
    }

    static String hashOf(MelAuthService instance) throws Exception {
        return Hash.sha256(instance.getCertificateManager().getMyX509Certificate().getEncoded());
    }

    /**
     * @return every entry the instance that asks has for the partner's certificate, trusted or not
     */
    static List<Certificate> entriesOf(MelAuthService partner, MelAuthService asking) throws Exception {
        String hash = hashOf(partner);
        List<Certificate> result = new ArrayList<>();
        for (Certificate certificate : asking.getCertificateManager().getAllCertificateDetails())
            if (hash.equals(certificate.getHash().v()))
                result.add(certificate);
        return result;
    }

    /**
     * @return the id of the partner's certificate in the database of the instance that asks
     */
    static Long idOf(MelAuthService partner, MelAuthService asking) throws Exception {
        List<Certificate> entries = entriesOf(partner, asking);
        assertEquals(1, entries.size(), asking.getName() + " should have exactly one entry for " + partner.getName());
        return entries.get(0).getId().v();
    }

    static boolean trusts(MelAuthService truster, MelAuthService trusted) {
        try {
            return truster.getCertificateManager().getTrustedCertificateByHash(hashOf(trusted)) != null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the connection that the instance which asks considers its normal connection to the partner, after waiting for it
     */
    static MelValidationProcess connect(MelAuthService from, MelAuthService to) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<MelValidationProcess> process = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        from.connect(idOf(to, from)).onDone(p -> {
            process.set(p);
            connected.countDown();
        }).onError(e -> {
            failure.set(e);
            connected.countDown();
        });
        assertTrue(connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), from.getName() + " did not connect to " + to.getName() + " in time");
        assertNull(failure.get(), from.getName() + " could not connect to " + to.getName());
        return process.get();
    }

    /**
     * Test services are only registered in memory. The access matrix refers to database rows, so make one and let the partner in.
     */
    static Long grant(MelAuthService owner, MelService service, MelAuthService partner) throws Exception {
        Long typeId = owner.getDatabaseManager().createServiceType("test type " + service.getUuid(), "only for tests").getId().v();
        Service row = new Service().setUuid(service.getUuid()).setTypeId(typeId).setName(service.getUuid()).setActive(true);
        Long serviceId = owner.getDatabaseManager().getSqlQueries().insert(row);
        owner.getDatabaseManager().grant(serviceId, idOf(partner, owner));
        return serviceId;
    }

    static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        return waitFor(condition, TIMEOUT_SECONDS);
    }

    static boolean waitFor(BooleanSupplier condition, int seconds) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean())
                return true;
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    /**
     * every socket the instance knows (no API for this)
     */
    @SuppressWarnings("unchecked")
    static List<MelSocket> socketsOf(MelAuthService instance) throws Exception {
        Field field = MelAuthServiceImpl.class.getDeclaredField("sockets");
        field.setAccessible(true);
        return new ArrayList<>((Set<MelSocket>) field.get(instance));
    }

    /**
     * Every open socket of the instance that carries a normal (validated) connection to the partner, registered or not.
     * There is no API for this (yet), so it looks into the instance.
     */
    static List<MelAuthSocket> openNormalConnections(MelAuthService instance, MelAuthService partner) throws Exception {
        Long partnerId = idOf(partner, instance);
        Field field = MelAuthServiceImpl.class.getDeclaredField("sockets");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<MelSocket> sockets = (Set<MelSocket>) field.get(instance);
        List<MelSocket> copy = new ArrayList<>(sockets);
        List<MelAuthSocket> result = new ArrayList<>();
        for (MelSocket socket : copy) {
            if (!(socket instanceof MelAuthSocket authSocket))
                continue;
            if (!(authSocket.getState() instanceof SocketState.Established established))
                continue;
            if (!Objects.equals(established.partner().getId().v(), partnerId))
                continue;
            if (authSocket.isStopped() || authSocket.getSocket() == null || authSocket.getSocket().isClosed())
                continue;
            result.add(authSocket);
        }
        return result;
    }

    /**
     * Pairing leaves a normal connection behind (the client connects after the registration). For tests that want to start without one.
     */
    static void closeNormalConnections(MelAuthService a, MelAuthService b) throws Exception {
        Long bAtA = idOf(b, a);
        Long aAtB = idOf(a, b);
        for (MelAuthSocket socket : openNormalConnections(a, b))
            socket.close(de.mel.auth.socket.CloseReason.SHUTDOWN);
        for (MelAuthSocket socket : openNormalConnections(b, a))
            socket.close(de.mel.auth.socket.CloseReason.SHUTDOWN);
        assertTrue(waitFor(() -> {
            try {
                return openNormalConnections(a, b).isEmpty() && openNormalConnections(b, a).isEmpty() && !a.isConnectedTo(bAtA) && !b.isConnectedTo(aAtB);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 10), "the connections made by the pairing did not go away");
    }

    /**
     * where the instance keeps its registered connections. No API for this either.
     */
    static ConnectedEnvironment environmentOf(MelAuthService instance) throws Exception {
        Field field = MelAuthServiceImpl.class.getDeclaredField("connectedEnvironment");
        field.setAccessible(true);
        return (ConnectedEnvironment) field.get(instance);
    }

    /**
     * @return whether the two sockets are the two ends of the same TCP connection
     */
    static boolean sameConnection(MelAuthSocket a, MelAuthSocket b) {
        return a.getSocket().getLocalPort() == b.getSocket().getPort() && a.getSocket().getPort() == b.getSocket().getLocalPort();
    }

    @Override
    public void close() {
        CountLock lock = new CountLock().lock();
        List<Promise<?, ?>> shut = new ArrayList<>();
        for (MelAuthService service : new MelAuthService[]{client, server}) {
            try {
                shut.add(service.shutDown());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Promises.allSettled(shut.toArray(new Promise[0])).onDone(r -> lock.unlock());
        lock.lock();
    }
}

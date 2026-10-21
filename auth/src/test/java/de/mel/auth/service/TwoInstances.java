package de.mel.auth.service;

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
import de.mel.auth.tools.ShutDownDeferredManager;
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
class TwoInstances implements AutoCloseable {
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
    final MelAuthService client;

    /**
     * @param portBase takes 10 ports from here on
     */
    TwoInstances(File dir, int portBase, IRegisterHandler serverUser, IRegisterHandler clientUser) throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        server = boot(dir, "SERVER", portBase, portBase + 1, portBase + 4, portBase + 6);
        client = boot(dir, "CLIENT", portBase + 2, portBase + 3, portBase + 5, portBase + 7);
        server.addRegisterHandler(serverUser);
        client.addRegisterHandler(clientUser);
    }

    private static MelAuthService boot(File parent, String name, int port, int deliveryPort, int brotcastListenerPort, int brotcastPort) throws Exception {
        File dir = new File(parent, name);
        dir.mkdirs();
        MelAuthSettings settings = new MelAuthSettings().setPort(port).setDeliveryPort(deliveryPort)
                .setBrotcastListenerPort(brotcastListenerPort).setBrotcastPort(brotcastPort)
                .setWorkingDirectory(dir).setName(name).setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(dir, MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        AtomicReference<MelAuthService> booted = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        new MelBoot(settings, new PowerManager(settings)).boot().done(service -> {
            booted.set(service);
            latch.countDown();
        }).fail(e -> {
            failure.set(e);
            latch.countDown();
        });
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), name + " did not boot in time");
        assertNull(failure.get(), name + " failed to boot");
        return booted.get();
    }

    /**
     * The client pairs with the server, both users have to accept.
     *
     * @return whether the client completed the pairing in time
     */
    boolean pairAgain() throws Exception {
        CountDownLatch registered = new CountDownLatch(1);
        client.addRegisteredHandler((service, cert) -> registered.countDown());
        client.connect("localhost", server.getSettings().getPort(), server.getSettings().getDeliveryPort(), true);
        return registered.await(10, TimeUnit.SECONDS);
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
        from.connect(idOf(to, from)).done(p -> {
            process.set(p);
            connected.countDown();
        }).fail(e -> {
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
        ShutDownDeferredManager shut = new ShutDownDeferredManager();
        for (MelAuthService service : new MelAuthService[]{client, server}) {
            try {
                shut.when(service.shutDown());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        shut.digest().done(r -> lock.unlock());
        lock.lock();
    }
}

package de.mel.auth.service;

import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.MelStrings;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.ShutDownDeferredManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pairing of two real Mel instances over loopback, with both users accepting or rejecting.
 * Pins down what each side is told and what is left in the certificate store afterwards.
 * Must live in this package: {@link MelAuthService#connect(String, int, int, boolean)} is package private.
 */
public class RegistrationTest {
    private static final int TIMEOUT_SECONDS = 30;
    private static final int USER_THINKING_MILLIS = 300;
    private static final AtomicInteger nextPortBase = new AtomicInteger(19000);

    /**
     * Plays the user: decides on every incoming certificate and records everything it is told.
     * Like a real user it decides later and not within the call: the client of a pairing sends its confirmation
     * before its request has left, so an instant answer would reach a server that has no registration to confirm yet.
     */
    private static class RecordingHandler implements IRegisterHandler {
        final List<String> events = new CopyOnWriteArrayList<>();
        private final boolean accept;
        private final boolean instant;

        RecordingHandler(boolean accept) {
            this(accept, false);
        }

        /**
         * @param instant decide within the call, like a handler that needs no user does
         */
        RecordingHandler(boolean accept, boolean instant) {
            this.accept = accept;
            this.instant = instant;
        }

        @Override
        public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
            events.add("acceptCertificate");
            if (instant) {
                if (accept)
                    listener.onCertificateAccepted(request, certificate);
                else
                    listener.onCertificateRejected(request, certificate);
                return;
            }
            Thread user = new Thread(() -> {
                try {
                    Thread.sleep(USER_THINKING_MILLIS);
                } catch (InterruptedException e) {
                    return;
                }
                if (accept)
                    listener.onCertificateAccepted(request, certificate);
                else
                    listener.onCertificateRejected(request, certificate);
            });
            user.setDaemon(true);
            user.start();
        }

        @Override
        public void onRegistrationCompleted(Certificate partnerCertificate) {
            events.add("onRegistrationCompleted");
        }

        @Override
        public void onRemoteRejected(Certificate partnerCertificate) {
            events.add("onRemoteRejected");
        }

        @Override
        public void onLocallyRejected(Certificate partnerCertificate) {
            events.add("onLocallyRejected");
        }

        @Override
        public void onRemoteAccepted(Certificate partnerCertificate) {
            events.add("onRemoteAccepted");
        }

        @Override
        public void onLocallyAccepted(Certificate partnerCertificate) {
            events.add("onLocallyAccepted");
        }
    }

    @TempDir
    File tempDir;

    private MelAuthService server;
    private MelAuthService client;
    private RecordingHandler serverUser;
    private RecordingHandler clientUser;
    private final List<String> serverRegistered = new CopyOnWriteArrayList<>();
    private final List<String> clientRegistered = new CopyOnWriteArrayList<>();

    private MelAuthService boot(String name, int port, int deliveryPort, int brotcastListenerPort, int brotcastPort) throws Exception {
        File dir = new File(tempDir, name);
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

    private void startInstances(boolean serverAccepts, boolean clientAccepts) throws Exception {
        startInstances(new RecordingHandler(serverAccepts), new RecordingHandler(clientAccepts));
    }

    private void startInstances(RecordingHandler serverUser, RecordingHandler clientUser) throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        // fresh ports for every test: the previous test's sockets may linger a moment after shutdown
        int base = nextPortBase.getAndAdd(10);
        server = boot("regtest-SERVER", base, base + 1, base + 4, base + 5);
        client = boot("regtest-CLIENT", base + 2, base + 3, base + 5, base + 4);
        this.serverUser = serverUser;
        this.clientUser = clientUser;
        server.addRegisterHandler(serverUser);
        client.addRegisterHandler(clientUser);
        server.addRegisteredHandler((service, cert) -> serverRegistered.add(cert.getName().v()));
        client.addRegisteredHandler((service, cert) -> clientRegistered.add(cert.getName().v()));
    }

    @AfterEach
    public void after() {
        CountLock lock = new CountLock().lock();
        ShutDownDeferredManager shut = new ShutDownDeferredManager();
        for (MelAuthService service : new MelAuthService[]{client, server}) {
            if (service != null)
                try {
                    shut.when(service.shutDown());
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
        shut.digest().done(r -> lock.unlock());
        lock.lock();
    }

    /**
     * Both instances also find each other by multicast and connect on their own. Now and then that automatic connection
     * swallows our pairing request, nobody is ever asked. Nothing has happened then, so ask again.
     */
    private void startPairing() throws Exception {
        // an earlier pairing may have left events behind: only new ones count
        int eventsBefore = clientUser.events.size() + serverUser.events.size();
        for (int attempt = 0; attempt < 4; attempt++) {
            client.connect("localhost", server.getSettings().getPort(), server.getSettings().getDeliveryPort(), true);
            if (waitFor(() -> clientUser.events.size() + serverUser.events.size() > eventsBefore, 8))
                return;
        }
        fail("pairing did not even start");
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        return waitFor(condition, TIMEOUT_SECONDS);
    }

    private static boolean waitFor(BooleanSupplier condition, int seconds) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean())
                return true;
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    /**
     * Absence cannot be waited for. Give a (wrong) late event the chance to show up.
     */
    private static void settle() throws InterruptedException {
        Thread.sleep(1500);
    }

    private static void assertNoTrustedPartner(MelAuthService service, String who) throws Exception {
        assertEquals(0, service.getCertificateManager().getTrustedCertificates().size(), who + " trusts someone it must not trust");
    }

    @Test
    public void bothAccept() throws Exception {
        startInstances(true, true);
        startPairing();
        assertTrue(waitFor(() -> serverUser.events.contains("onRegistrationCompleted")
                        && clientUser.events.contains("onRegistrationCompleted")),
                "registration did not complete on both sides: server=" + serverUser.events + " client=" + clientUser.events);

        for (RecordingHandler user : new RecordingHandler[]{serverUser, clientUser}) {
            assertTrue(user.events.contains("acceptCertificate"), "the user was never asked: " + user.events);
            assertTrue(user.events.contains("onLocallyAccepted"), user.events.toString());
            assertTrue(user.events.contains("onRemoteAccepted"), user.events.toString());
            assertFalse(user.events.contains("onLocallyRejected"), user.events.toString());
            assertFalse(user.events.contains("onRemoteRejected"), user.events.toString());
        }

        List<Certificate> trustedByClient = client.getCertificateManager().getTrustedCertificates();
        List<Certificate> trustedByServer = server.getCertificateManager().getTrustedCertificates();
        assertEquals(1, trustedByClient.size());
        assertEquals(1, trustedByServer.size());
        assertEquals("regtest-SERVER", trustedByClient.get(0).getName().v());
        assertEquals("regtest-CLIENT", trustedByServer.get(0).getName().v());
        // each side stored exactly the certificate the other one actually owns
        assertArrayEquals(server.getCertificateManager().getMyX509Certificate().getEncoded(), trustedByClient.get(0).getCertificate().v());
        assertArrayEquals(client.getCertificateManager().getMyX509Certificate().getEncoded(), trustedByServer.get(0).getCertificate().v());

        assertTrue(waitFor(() -> serverRegistered.contains("regtest-CLIENT") && clientRegistered.contains("regtest-SERVER")),
                "registered handlers: server=" + serverRegistered + " client=" + clientRegistered);
    }

    @Test
    public void serverUserRejects() throws Exception {
        startInstances(false, true);
        startPairing();
        assertTrue(waitFor(() -> serverUser.events.contains("acceptCertificate")), "the server's user was never asked");
        assertTrue(waitFor(() -> clientUser.events.contains("onRemoteRejected")),
                "the client was not told about the rejection: " + clientUser.events);
        settle();

        assertFalse(clientUser.events.contains("onRegistrationCompleted"), clientUser.events.toString());
        assertFalse(serverUser.events.contains("onRegistrationCompleted"), serverUser.events.toString());
        assertNoTrustedPartner(client, "client");
        assertNoTrustedPartner(server, "server");
        assertTrue(serverRegistered.isEmpty(), "server completed a registration it rejected: " + serverRegistered);
        assertTrue(clientRegistered.isEmpty(), "client completed a registration that was rejected: " + clientRegistered);
        assertTrue(client.getCertificateManager().getAllCertificateDetails().isEmpty(),
                "the client keeps the certificate of the server that rejected it");
    }

    @Test
    public void clientUserRejects() throws Exception {
        startInstances(true, false);
        startPairing();
        assertTrue(waitFor(() -> clientUser.events.contains("onLocallyRejected")),
                "the client's user rejection was not reported: " + clientUser.events);
        settle();

        assertFalse(clientUser.events.contains("onRegistrationCompleted"), clientUser.events.toString());
        assertFalse(serverUser.events.contains("onRegistrationCompleted"), serverUser.events.toString());
        assertNoTrustedPartner(client, "client");
        assertTrue(clientRegistered.isEmpty(), "client completed a registration its user rejected: " + clientRegistered);
        assertTrue(client.getCertificateManager().getAllCertificateDetails().isEmpty(),
                "the client keeps the certificate of a server its user rejected");
        // the server's user accepted, but the pairing is incomplete without the client
        assertTrue(serverUser.events.contains("onRemoteRejected"), "the server was not told that the client rejected: " + serverUser.events);
    }

    @Test
    public void bothReject() throws Exception {
        startInstances(false, false);
        startPairing();
        assertTrue(waitFor(() -> clientUser.events.contains("onLocallyRejected") || clientUser.events.contains("onRemoteRejected")),
                "the rejection was not reported to the client: " + clientUser.events);
        settle();

        assertFalse(clientUser.events.contains("onRegistrationCompleted"), clientUser.events.toString());
        assertFalse(serverUser.events.contains("onRegistrationCompleted"), serverUser.events.toString());
        assertNoTrustedPartner(client, "client");
        assertNoTrustedPartner(server, "server");
    }

    @Test
    public void registrationIsRepeatableAfterARejection() throws Exception {
        startInstances(false, true);
        startPairing();
        assertTrue(waitFor(() -> clientUser.events.contains("onRemoteRejected")), clientUser.events.toString());
        settle();
        assertNoTrustedPartner(client, "client");

        // the server's user changed their mind
        RecordingHandler accepting = new RecordingHandler(true);
        server.getRegisterHandlers().clear();
        server.addRegisterHandler(accepting);
        startPairing();
        assertTrue(waitFor(() -> accepting.events.contains("onRegistrationCompleted")
                        && clientUser.events.contains("onRegistrationCompleted")),
                "second attempt did not complete: server=" + accepting.events + " client=" + clientUser.events);
        assertEquals(1, client.getCertificateManager().getTrustedCertificates().size());
        assertEquals(1, server.getCertificateManager().getTrustedCertificates().size());
    }

    /**
     * MelRegisterProcess.register() runs the client's handlers, and with them sends the client's confirmation, before the
     * registration request has left. A handler that decides within the call (ACCEPT_ALL, no user to ask) makes the client confirm to a
     * server that has no registration process yet. The confirmation is dropped, the server never completes the registration
     * although both sides accepted.
     */
    @Test
    public void bothAcceptInstantly() throws Exception {
        startInstances(new RecordingHandler(true, true), new RecordingHandler(true, true));
        startPairing();
        assertTrue(waitFor(() -> clientUser.events.contains("onRegistrationCompleted")),
                "client did not complete: " + clientUser.events);
        assertTrue(waitFor(() -> serverUser.events.contains("onRegistrationCompleted"), 10),
                "both accepted, yet the server never completed the registration: server=" + serverUser.events + " client=" + clientUser.events);
        assertTrue(serverUser.events.contains("onRemoteAccepted"), serverUser.events.toString());
    }

    /**
     * Several register handlers are normal (a dialog, a log, ...). Each of them is asked, but the registration still is
     * one registration: the client must report the partner to its registered handlers exactly once.
     * register() hooks the answer of the request up inside the loop over the register handlers, so it runs once per handler.
     */
    @Test
    public void clientWithTwoRegisterHandlersReportsTheRegistrationOnce() throws Exception {
        startInstances(new RecordingHandler(true), new RecordingHandler(true));
        // a second handler nobody ever answers
        client.addRegisterHandler(new RecordingHandler(true) {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
                events.add("acceptCertificate");
            }
        });
        startPairing();
        assertTrue(waitFor(() -> clientUser.events.contains("onRegistrationCompleted")),
                "client did not complete: " + clientUser.events);
        assertTrue(waitFor(() -> !clientRegistered.isEmpty()), "client never reported the registration");
        settle();
        assertEquals(List.of("regtest-SERVER"), clientRegistered,
                "the client reported the same registration more than once");
    }
}

package de.mel.auth.service;

import de.mel.auth.MelStrings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.IsolationDetails;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.access.CertificateCreator;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.ShutDownDeferredManager;
import de.mel.core.serialize.SerializableEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.io.File;
import java.net.SocketException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real sockets, real TLS, real pairing - on loopback with two in-process Mel instances.
 * Pins down how connections are authenticated, independent of how the handshake is implemented.
 * Must live in this package: {@link MelAuthService#connect(String, int, int, boolean)} is package private.
 */
@Tag("integration")
public class AuthHandshakeTest {
    private static final int TIMEOUT_SECONDS = 30;

    @TempDir
    File tempDir;

    /**
     * accepts everyone, so pairing needs no user
     */
    private static final IRegisterHandler ACCEPT_ALL = new IRegisterHandler() {
        @Override
        public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
            listener.onCertificateAccepted(request, certificate);
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
    };

    private static final java.util.concurrent.atomic.AtomicInteger nextPortBase = new java.util.concurrent.atomic.AtomicInteger(18000);

    private IsolationRecordingService serverService;
    private IsolationRecordingService clientService;
    private MelAuthService server;
    private MelAuthService client;

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

    @BeforeEach
    public void before() throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        // fresh ports for every test: the previous test's sockets may linger a moment after shutdown
        int base = nextPortBase.getAndAdd(10);
        server = boot("authtest-server", base, base + 1, base + 4, base + 5);
        client = boot("authtest-client", base + 2, base + 3, base + 5, base + 4);
        serverService = new IsolationRecordingService(server, new File(tempDir, "server-service"), "server test service");
        clientService = new IsolationRecordingService(client, new File(tempDir, "client-service"), "client test service");
        server.registerMelService(serverService);
        client.registerMelService(clientService);
        server.addRegisterHandler(ACCEPT_ALL);
        client.addRegisterHandler(ACCEPT_ALL);
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
     * pairs client with server and returns the id of the server's certificate in the client's database
     */
    private Long pair() throws Exception {
        CountDownLatch registered = new CountDownLatch(1);
        client.addRegisteredHandler((service, cert) -> registered.countDown());
        // Both instances also find each other by multicast and connect on their own. Now and then that automatic connection
        // swallows our pairing request (certificate fetched, never registered). The first attempt then never completes, so retry.
        boolean paired = false;
        for (int attempt = 0; attempt < 4 && !paired; attempt++) {
            client.connect("localhost", server.getSettings().getPort(), server.getSettings().getDeliveryPort(), true);
            paired = registered.await(8, TimeUnit.SECONDS);
        }
        assertTrue(paired, "pairing did not complete");
        List<Certificate> trusted = client.getCertificateManager().getTrustedCertificates();
        assertEquals(1, trusted.size());
        return trusted.get(0).getId().v();
    }

    @Test
    public void pairedClientAuthenticates() throws Exception {
        Long serverCertId = pair();
        CountDownLatch authenticated = new CountDownLatch(1);
        AtomicReference<MelValidationProcess> process = new AtomicReference<>();
        client.connect(serverCertId).done(p -> {
            process.set(p);
            authenticated.countDown();
        });
        assertTrue(authenticated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "client did not authenticate against the server");
        assertNotNull(process.get());
        assertTrue(client.isConnectedTo(serverCertId));
        Long clientCertId = server.getCertificateManager().getTrustedCertificates().get(0).getId().v();
        assertTrue(server.isConnectedTo(clientCertId), "server does not consider the client connected");
    }

    /**
     * suspend() shuts the worker (socket opener, certificate delivery, brotcaster) and all sockets down, resume() starts a new worker.
     * Afterwards the instance has to be reachable again: new sockets, the old pairing still counts.
     */
    @Test
    public void pairedClientCanConnectAgainAfterTheServerWasSuspendedAndResumed() throws Exception {
        Long serverCertId = pair();
        server.suspend();
        server.resume();

        CountDownLatch authenticated = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        // the suspended server dropped the sockets, wait until the client knows
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (client.isConnectedTo(serverCertId) && System.nanoTime() < end)
            Thread.sleep(50);
        client.connect(serverCertId).done(p -> authenticated.countDown()).fail(e -> {
            failure.set(e);
            authenticated.countDown();
        });
        assertTrue(authenticated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "client could not connect to the resumed server");
        assertNull(failure.get(), "connecting to the resumed server failed");
        assertTrue(client.isConnectedTo(serverCertId));
    }

    /**
     * Plays an attacker: a TLS client that is not any of the paired devices and tries to get authenticated by the server.
     * It sends an auth request and listens to what comes back. How that travels is {@link RawPeer}'s business.
     *
     * @param isolated      ask for an isolated connection to the server's service
     * @param presentedCert the certificate shown during the TLS handshake, null = none at all
     * @param signingKey    the key that signs the handshake. Whoever shows a certificate without owning its key fails right there.
     * @return the last thing the server said, or null if it hung up or stayed silent
     */
    private SerializableEntity impersonate(boolean isolated, X509Certificate presentedCert, PrivateKey signingKey) throws Exception {
        IsolationDetails isolation = null;
        if (isolated) {
            isolation = new IsolationDetails()
                    .setTargetService(serverService.getUuid())
                    .setSourceService("attackers service")
                    .setIsolationUuid(UUID.randomUUID().toString())
                    .setProcessClass(TestIsolatedProcess.class.getName());
        }
        try (RawPeer attacker = RawPeer.connect(server.getSettings().getPort(), presentedCert, signingKey)) {
            attacker.send(RawPeer.authRequest(isolation));
            return attacker.receive(3000);
        } catch (EOFException | SocketException | SSLException e) {
            // hung up on us or failed the handshake: fine
            return null;
        }
    }

    private void assertNothingLeaked(SerializableEntity lastAnswer) {
        if (lastAnswer instanceof de.mel.auth.data.MelResponse) {
            de.mel.auth.data.MelResponse response = (de.mel.auth.data.MelResponse) lastAnswer;
            assertEquals(MelStrings.msg.STATE_ERR, response.getState(), "an impostor got a non-error answer");
            assertNull(response.getPayload(), "an impostor got a payload (the allowed services?)");
        }
    }

    /**
     * @return certificate and key of someone the server was never paired with
     */
    private KeyPair strangersKeys() throws Exception {
        return CertificateCreator.generateKeyPair(2048);
    }

    @Test
    public void clientWithoutCertificateIsNotAuthenticated() throws Exception {
        pair();
        Certificate pairedClient = server.getCertificateManager().getTrustedCertificates().get(0);
        SerializableEntity answer = impersonate(false, null, null);
        assertNothingLeaked(answer);
        assertFalse(server.isConnectedTo(pairedClient.getId().v()), "a client without any certificate got in as the paired client");
    }

    @Test
    public void clientWithAnUnknownCertificateIsNotAuthenticated() throws Exception {
        pair();
        Certificate pairedClient = server.getCertificateManager().getTrustedCertificates().get(0);
        KeyPair stranger = strangersKeys();
        SerializableEntity answer = impersonate(false, CertificateCreator.generateCertificate(stranger, "stranger"), stranger.getPrivate());
        assertNothingLeaked(answer);
        assertFalse(server.isConnectedTo(pairedClient.getId().v()));
        assertEquals(1, server.getCertificateManager().getTrustedCertificates().size(), "the server trusts someone new");
    }

    @Test
    public void unpairedClientCannotConnect() throws Exception {
        CountDownLatch authenticated = new CountDownLatch(1);
        // regOnUnknown = false: no pairing allowed here
        client.connect("localhost", server.getSettings().getPort(), server.getSettings().getDeliveryPort(), false)
                .done(p -> authenticated.countDown());
        assertFalse(authenticated.await(5, TimeUnit.SECONDS), "an unpaired client got authenticated");
        assertTrue(server.getCertificateManager().getTrustedCertificates().isEmpty(), "server trusts a client it was never paired with");
    }

    @Test
    public void clientIsRefusedOnceTheServerStoppedTrustingIt() throws Exception {
        Long serverCertId = pair();
        Certificate pairedClient = server.getCertificateManager().getTrustedCertificates().get(0);
        server.getCertificateManager().trustCertificate(pairedClient.getId().v(), false);
        CountDownLatch authenticated = new CountDownLatch(1);
        client.connect(serverCertId).done(p -> authenticated.countDown());
        assertFalse(authenticated.await(8, TimeUnit.SECONDS), "client was authenticated although the server no longer trusts it");
        assertFalse(server.isConnectedTo(pairedClient.getId().v()));
    }

    /**
     * Test services are only registered in memory. The access matrix refers to database rows, so make one.
     */
    private Long serverServiceId() throws Exception {
        Long typeId = server.getDatabaseManager().createServiceType("test type", "only for AuthHandshakeTest").getId().v();
        Service row = new Service().setUuid(serverService.getUuid()).setTypeId(typeId).setName("server test service").setActive(true);
        return server.getDatabaseManager().getSqlQueries().insert(row);
    }

    @Test
    public void pairedClientCanOpenAnIsolatedConnection() throws Exception {
        Long serverCertId = pair();
        server.getDatabaseManager().grant(serverServiceId(), server.getCertificateManager().getTrustedCertificates().get(0).getId().v());
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<MelIsolatedProcess> process = new AtomicReference<>();
        client.connectToService(TestIsolatedProcess.class, serverCertId, serverService.getUuid(), clientService.getUuid(), null, null, null)
                .done(p -> {
                    process.set(p);
                    opened.countDown();
                });
        assertTrue(opened.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "isolated connection was not established on the client side");
        assertTrue(serverService.firstEstablished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "server service was never handed the isolated connection");
        MelIsolatedProcess serverSide = serverService.established.get(0);
        assertEquals(TestIsolatedProcess.class, serverSide.getClass());
        assertEquals(clientService.getUuid(), serverSide.getPartnerServiceUuid());
        Long clientCertId = server.getCertificateManager().getTrustedCertificates().get(0).getId().v();
        assertEquals(clientCertId, serverSide.getPartnerCertificateId());
    }

    /**
     * Being paired is not enough: the access matrix decides which services a partner may talk to.
     * Normal requests are checked against it (MelValidationProcess.isServiceAllowed), isolated connections must be, too.
     */
    @Test
    public void pairedClientWithoutAccessCannotOpenAnIsolatedConnection() throws Exception {
        Long serverCertId = pair();
        // paired, but the server never granted this client access to its service
        Long clientCertId = server.getCertificateManager().getTrustedCertificates().get(0).getId().v();
        Long serviceId = serverServiceId();
        assertFalse(server.getDatabaseManager().isApproved(clientCertId, serviceId), "test setup: client must not have access yet");
        CountDownLatch opened = new CountDownLatch(1);
        client.connectToService(TestIsolatedProcess.class, serverCertId, serverService.getUuid(), clientService.getUuid(), null, null, null)
                .done(p -> opened.countDown());
        assertFalse(serverService.firstEstablished.await(5, TimeUnit.SECONDS), "a partner without access was handed an isolated connection to the service");
        assertTrue(serverService.established.isEmpty());
        assertEquals(1, opened.getCount(), "the client believes it has an isolated connection to a service it has no access to");
    }

    /**
     * Certificate ids are Longs. Ids above 127 are no longer cached boxed objects, so comparing them with == breaks.
     */
    @Test
    public void isolatedConnectionWorksWithLargeCertificateIds() throws Exception {
        for (int i = 0; i < 130; i++) {
            Certificate filler = client.getCertificateManager().importCertificate(
                    CertificateCreator.generateCertificate(strangersKeys(), "filler" + i), "filler" + i, "localhost", 1, 1);
            client.getCertificateManager().trustCertificate(filler.getId().v(), false);
        }
        Long serverCertId = pair();
        assertTrue(serverCertId > 127, "test setup: the server's certificate id must be above 127, was " + serverCertId);
        server.getDatabaseManager().grant(serverServiceId(), server.getCertificateManager().getTrustedCertificates().get(0).getId().v());
        CountDownLatch opened = new CountDownLatch(1);
        client.connectToService(TestIsolatedProcess.class, serverCertId, serverService.getUuid(), clientService.getUuid(), null, null, null)
                .done(p -> opened.countDown());
        assertTrue(opened.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "isolated connection failed for a certificate id above 127");
    }

    @Test
    public void pairedClientWithAccessCanOpenAnIsolatedConnection() throws Exception {
        Long serverCertId = pair();
        Long clientCertId = server.getCertificateManager().getTrustedCertificates().get(0).getId().v();
        Long serviceId = serverServiceId();
        server.getDatabaseManager().grant(serviceId, clientCertId);
        CountDownLatch opened = new CountDownLatch(1);
        client.connectToService(TestIsolatedProcess.class, serverCertId, serverService.getUuid(), clientService.getUuid(), null, null, null)
                .done(p -> opened.countDown());
        assertTrue(opened.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "a partner with access could not open an isolated connection");
        assertTrue(serverService.firstEstablished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void impostorCannotOpenAnIsolatedConnection() throws Exception {
        pair();
        KeyPair stranger = strangersKeys();
        SerializableEntity answer = impersonate(true, CertificateCreator.generateCertificate(stranger, "stranger"), stranger.getPrivate());
        assertNothingLeaked(answer);
        assertFalse(serverService.firstEstablished.await(2, TimeUnit.SECONDS), "an impostor was handed an isolated connection to a service");
        assertTrue(serverService.established.isEmpty());
    }

    @Test
    public void clientWithoutCertificateCannotOpenAnIsolatedConnection() throws Exception {
        pair();
        SerializableEntity answer = impersonate(true, null, null);
        assertNothingLeaked(answer);
        assertFalse(serverService.firstEstablished.await(2, TimeUnit.SECONDS), "a client without certificate was handed an isolated connection to a service");
    }

    @Test
    public void trustedCertificateWithTheWrongKeyIsNotAuthenticated() throws Exception {
        pair();
        Certificate pairedClient = server.getCertificateManager().getTrustedCertificates().get(0);
        X509Certificate victimsCertificate = client.getCertificateManager().getMyX509Certificate();
        // shows the victim's public certificate but can only sign with a key of its own
        SerializableEntity answer = impersonate(false, victimsCertificate, strangersKeys().getPrivate());
        assertNothingLeaked(answer);
        assertFalse(server.isConnectedTo(pairedClient.getId().v()), "someone with the victim's public certificate but not its key got in");
    }
}

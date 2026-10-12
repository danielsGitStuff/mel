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
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyPair;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
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
     * Plays an attacker: a TLS client that is not any of the paired devices and tries to get authenticated by the server.
     * This is the only part of the negative tests that knows the auth protocol: it sends an auth request and listens to what comes back.
     *
     * @param isolated      ask for an isolated connection to the server's service
     * @param presentedCert the certificate shown during the TLS handshake, null = none at all
     * @param signingKey    the key that signs the handshake. Whoever shows a certificate without owning its key fails right there.
     * @return the last thing the server said, or null if it hung up or stayed silent
     */
    private SerializableEntity impersonate(boolean isolated, X509Certificate presentedCert, PrivateKey signingKey) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        KeyManager[] keyManagers = null;
        if (presentedCert != null) {
            keyManagers = new KeyManager[]{new X509KeyManager() {
                public String[] getClientAliases(String keyType, Principal[] issuers) {
                    return new String[]{"attacker"};
                }

                public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                    return "attacker";
                }

                public String[] getServerAliases(String keyType, Principal[] issuers) {
                    return null;
                }

                public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                    return null;
                }

                public X509Certificate[] getCertificateChain(String alias) {
                    return new X509Certificate[]{presentedCert};
                }

                public PrivateKey getPrivateKey(String alias) {
                    return signingKey;
                }
            }};
        }
        // an attacker does not care who the server is
        context.init(keyManagers, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());
        SerializableEntity last = null;
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress("localhost", server.getSettings().getPort()), 5000);
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            MelRequest request = new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_AUTH);
            if (isolated) {
                request.setPayLoad(new IsolationDetails()
                        .setTargetService(serverService.getUuid())
                        .setSourceService("attackers service")
                        .setIsolationUuid(UUID.randomUUID().toString())
                        .setProcessClass(TestIsolatedProcess.class.getName()));
            }
            out.writeUTF(request.toJSON());
            out.flush();
            last = SerializableEntityDeserializer.deserialize(in.readUTF());
        } catch (EOFException | SocketTimeoutException | SSLException e) {
            // hung up on us, stayed silent or failed the handshake: fine
        }
        return last;
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

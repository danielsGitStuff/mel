package de.mel.auth.service;

import de.mel.auth.data.access.CertificateCreator;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.UntrustedPartnerException;
import de.mel.auth.socket.WrongPartnerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Who a connection belongs to is decided by the certificate the other side showed in the TLS handshake, never by the address it was found at.
 * Both sides check that the other one is trusted.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class PartnerIdentityTest {
    private static final int FAKE_PORT = 29080;

    @TempDir
    File tempDir;

    private TwoInstances instances;
    private MelAuthService server;
    private MelAuthService client;
    private Long serverIdAtClient;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, 29000, ACCEPT_ALL, ACCEPT_ALL);
        server = instances.server;
        client = instances.client;
        instances.pair();
        serverIdAtClient = idOf(server, client);
        // pairing also left a normal connection behind: get rid of it, we want to connect on purpose
        closeNormalConnections(client, server);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    /**
     * Every row in the certificate table as text, by id. The address and port are left out when asked.
     */
    private Map<Long, String> rows(MelAuthService instance, boolean withAddress) throws Exception {
        Map<Long, String> result = new TreeMap<>();
        for (Certificate listed : instance.getCertificateManager().getAllCertificateDetails()) {
            // the list only has some columns
            Certificate c = instance.getCertificateManager().getCertificateById(listed.getId().v());
            result.put(c.getId().v(), c.getName().v() + "|" + c.getTrusted().v() + "|" + c.getHash().v()
                    + (withAddress ? "|" + c.getAddress().v() + "|" + c.getPort().v() + "|" + c.getCertDeliveryPort().v() : ""));
        }
        return result;
    }

    private static class Outcome {
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final CountDownLatch finished = new CountDownLatch(1);
        volatile boolean resolved;
    }

    private Outcome connectTo(Long certId) throws Exception {
        Outcome outcome = new Outcome();
        client.connect(certId).onDone(p -> {
            outcome.resolved = true;
            outcome.finished.countDown();
        }).onError(e -> {
            outcome.failure.set(e);
            outcome.finished.countDown();
        });
        assertTrue(outcome.finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "connect did not come to an end");
        return outcome;
    }

    /**
     * Someone is at the partner's address whose certificate was imported (for a pairing that never completed) but is not trusted.
     * TLS accepts it. We must not.
     */
    @Test
    public void anUntrustedCertificateAtThePartnersAddressIsRefusedAndNothingIsStored() throws Exception {
        KeyPair keys = CertificateCreator.generateKeyPair(2048);
        X509Certificate stranger = CertificateCreator.generateCertificate(keys, "stranger");
        client.getCertificateManager().importCertificate(stranger, "stranger", "localhost", FAKE_PORT, FAKE_PORT);
        client.updateCertAddresses(serverIdAtClient, "localhost", FAKE_PORT, FAKE_PORT);
        Map<Long, String> before = rows(client, true);
        Map<Long, String> serverBefore = rows(server, true);
        try (FakePartner fake = new FakePartner(FAKE_PORT, stranger, keys.getPrivate())) {
            Outcome outcome = connectTo(serverIdAtClient);
            assertFalse(outcome.resolved, "the client connected to somebody it does not trust");
            assertInstanceOf(UntrustedPartnerException.class, outcome.failure.get());
            assertTrue(fake.sawHangUpWithin(10), "the client kept the connection to the untrusted one open");
            assertFalse(fake.authRequest.isDone(), "the client told an untrusted one what it wants");
        }
        assertFalse(client.isConnectedTo(serverIdAtClient));
        assertEquals(before, rows(client, true), "the certificate table changed");
        assertEquals(serverBefore, rows(server, true));
    }

    /**
     * The address of a partner now belongs to another one of our partners. The connection is a connection to the one that is really there,
     * the connect that was meant for the first one fails, and the only change in the database is the real one's address and port.
     */
    @Test
    public void anotherTrustedPartnerAtTheAddressGetsTheConnectionAndOnlyItsAddressIsUpdated() throws Exception {
        KeyPair keys = CertificateCreator.generateKeyPair(2048);
        X509Certificate someoneElse = CertificateCreator.generateCertificate(keys, "someone else");
        Certificate expected = client.getCertificateManager().importCertificate(someoneElse, "someone else", "localhost", server.getSettings().getPort(), server.getSettings().getDeliveryPort());
        client.getCertificateManager().trustCertificate(expected.getId().v(), true);
        // the real server's address in the database is stale
        client.updateCertAddresses(serverIdAtClient, "10.9.8.7", 1234, 1235);
        Map<Long, String> beforeWithoutAddress = rows(client, false);
        Map<Long, String> beforeWithAddress = rows(client, true);
        Map<Long, String> serverBefore = rows(server, true);

        Outcome outcome = connectTo(expected.getId().v());
        assertFalse(outcome.resolved, "connect(expected) must not claim to be connected to the expected partner");
        assertInstanceOf(WrongPartnerException.class, outcome.failure.get());

        // the connection belongs to the one that is really there
        assertTrue(waitFor(() -> client.isConnectedTo(serverIdAtClient), 10), "the connection to the partner that answered was not kept");
        assertFalse(client.isConnectedTo(expected.getId().v()), "the connection was booked as one to the partner that did not answer");
        // database: nothing but the address and port of the one that answered
        assertEquals(beforeWithoutAddress, rows(client, false));
        Map<Long, String> after = rows(client, true);
        for (Long id : beforeWithAddress.keySet())
            if (!id.equals(serverIdAtClient))
                assertEquals(beforeWithAddress.get(id), after.get(id), "row " + id + " must not change");
        Certificate server = client.getCertificateManager().getTrustedCertificateById(serverIdAtClient);
        assertEquals("127.0.0.1", server.getAddress().v());
        assertEquals(this.server.getSettings().getPort(), server.getPort().v());
        assertEquals(serverBefore, rows(this.server, true));
    }
}

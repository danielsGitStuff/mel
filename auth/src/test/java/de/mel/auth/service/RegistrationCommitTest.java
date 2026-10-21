package de.mel.auth.service;

import de.mel.auth.data.access.CertificateCreator;
import de.mel.auth.data.db.Certificate;
import de.mel.sql.Hash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * When a registration is persisted and what it does to an entry that exists already.
 * See SOCKET_PROCESS_REWORK.md: the mutual confirmation is the commit; a known partner may register again.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class RegistrationCommitTest {
    private static final AtomicInteger nextPortBase = new AtomicInteger(25000);

    @TempDir
    File tempDir;

    private TwoInstances instances;

    @AfterEach
    public void after() {
        if (instances != null)
            instances.close();
    }

    /**
     * The client lost the server (it deleted it, or the registration only completed on the server's side) and pairs again.
     * The server knows the client already: the entry stays the same, so the services the client may use stay, too.
     */
    @Disabled("fails on the old code: importing a known certificate violates UNIQUE (certificate), see SOCKET_PROCESS_REWORK.md")
    @Test
    public void aKnownPartnerCanRegisterAgainAndKeepsItsAccess() throws Exception {
        instances = new TwoInstances(tempDir, nextPortBase.getAndAdd(10), ACCEPT_ALL, ACCEPT_ALL);
        MelAuthService server = instances.server;
        MelAuthService client = instances.client;
        instances.pair();
        MelTestService service = new MelTestService(server, new File(tempDir, "service"), 1L, "a service");
        server.registerMelService(service);
        Long serviceId = grant(server, service, client);
        Long clientId = idOf(client, server);
        for (Certificate certificate : entriesOf(server, client))
            client.getCertificateManager().deleteCertificate(certificate);

        assertTrue(instances.pairAgain(), "the client could not pair again with a server that knows it already");
        assertTrue(waitFor(() -> trusts(client, server)), "the client does not trust the server");
        List<Certificate> entries = entriesOf(client, server);
        assertEquals(1, entries.size(), "the server should still have exactly one entry for the client");
        assertEquals(clientId, entries.get(0).getId().v(), "the server replaced its entry for the client");
        assertTrue(trusts(server, client), "the server does not trust the client anymore");
        assertTrue(server.getDatabaseManager().isApproved(clientId, serviceId), "the client lost its access to the service");
    }

    /**
     * A rejected registration changes nothing on the side that knew the partner already.
     */
    @Test
    public void rejectingTheRegistrationOfAKnownPartnerKeepsIt() throws Exception {
        SwitchableUser serverUser = new SwitchableUser(true);
        instances = new TwoInstances(tempDir, nextPortBase.getAndAdd(10), serverUser, ACCEPT_ALL);
        MelAuthService server = instances.server;
        MelAuthService client = instances.client;
        instances.pair();
        MelTestService service = new MelTestService(server, new File(tempDir, "service"), 1L, "a service");
        server.registerMelService(service);
        Long serviceId = grant(server, service, client);
        Long clientId = idOf(client, server);
        for (Certificate certificate : entriesOf(server, client))
            client.getCertificateManager().deleteCertificate(certificate);

        serverUser.accept = false;
        assertFalse(instances.pairAgain(), "the client completed a registration the server's user rejected");
        Thread.sleep(1500);
        assertFalse(trusts(client, server), "the client trusts a server that rejected it");
        List<Certificate> entries = entriesOf(client, server);
        assertEquals(1, entries.size(), "the server should still have exactly one entry for the client");
        assertEquals(clientId, entries.get(0).getId().v());
        assertTrue(trusts(server, client), "the rejection removed the trust the server had before");
        assertTrue(server.getDatabaseManager().isApproved(clientId, serviceId), "the rejection removed the client's access to the service");
    }

    /**
     * The same as the hasty partner below, but this one stays until the server trusts it.
     * Shows that the hand-made registration works, so a failure below is about the hang-up.
     */
    @Test
    public void theLastDecisionCountsWhenThePartnerStays() throws Exception {
        instances = new TwoInstances(tempDir, nextPortBase.getAndAdd(10), ACCEPT_ALL, ACCEPT_ALL);
        MelAuthService server = instances.server;
        KeyPair keys = CertificateCreator.generateKeyPair(2048);
        X509Certificate certificate = CertificateCreator.generateCertificate(keys, "patient");
        String hash = Hash.sha256(certificate.getEncoded());
        try (RawPeer patient = RawPeer.connect(server.getSettings().getPort(), certificate, keys.getPrivate())) {
            patient.send(RawPeer.registerRequest("patient", certificate, 1, 2));
            patient.send(RawPeer.registerDecision(true));
            assertTrue(waitFor(() -> {
                try {
                    return server.getCertificateManager().getTrustedCertificateByHash(hash) != null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 10), "the server did not trust a partner that registered and stayed");
        }
    }

    /**
     * A partner sends its last registration message and hangs up right away. The message arrived, so it counts:
     * a close by the partner must not cut off what was received before it.
     */
    @Test
    public void theLastDecisionCountsAlthoughThePartnerHangsUpRightBehindIt() throws Exception {
        instances = new TwoInstances(tempDir, nextPortBase.getAndAdd(10), ACCEPT_ALL, ACCEPT_ALL);
        MelAuthService server = instances.server;
        int rounds = 10;
        int lost = 0;
        for (int round = 0; round < rounds; round++) {
            KeyPair keys = CertificateCreator.generateKeyPair(2048);
            X509Certificate certificate = CertificateCreator.generateCertificate(keys, "hasty" + round);
            String hash = Hash.sha256(certificate.getEncoded());
            try (RawPeer hasty = RawPeer.connect(server.getSettings().getPort(), certificate, keys.getPrivate())) {
                hasty.send(RawPeer.registerRequest("hasty" + round, certificate, 1, 2));
                hasty.send(RawPeer.registerDecision(true));
            }
            boolean trusted = waitFor(() -> {
                try {
                    return server.getCertificateManager().getTrustedCertificateByHash(hash) != null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 5);
            if (!trusted)
                lost++;
        }
        assertEquals(0, lost, "in " + lost + " of " + rounds + " rounds the server lost the last decision");
    }
}

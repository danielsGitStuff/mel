package de.mel.auth.service;

import de.mel.auth.MelStrings;
import de.mel.auth.data.MelResponse;
import de.mel.auth.data.access.CertificateCreator;
import de.mel.core.serialize.SerializableEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A message that does not fit the state of the connection is a protocol violation: the instance closes the connection.
 * Nothing is dropped silently, nothing waits for something that will never come.
 * Once connections are closed with a reason, these tests check the reason, too.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class ProtocolViolationTest {
    private static final AtomicInteger nextPortBase = new AtomicInteger(24000);
    private static final int HANG_UP_MILLIS = 5000;

    @TempDir
    File tempDir;

    private TwoInstances instances;
    private MelAuthService server;
    private MelAuthService client;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, nextPortBase.getAndAdd(10), ACCEPT_ALL, ACCEPT_ALL);
        server = instances.server;
        client = instances.client;
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    private RawPeer stranger() throws Exception {
        KeyPair keys = CertificateCreator.generateKeyPair(2048);
        return RawPeer.connect(server.getSettings().getPort(), CertificateCreator.generateCertificate(keys, "stranger"), keys.getPrivate());
    }

    /**
     * plays the paired client with its real certificate and key, and is authenticated as such
     */
    private RawPeer authenticatedClient() throws Exception {
        instances.pair();
        RawPeer peer = RawPeer.connect(server.getSettings().getPort(), client.getCertificateManager().getMyX509Certificate(), client.getCertificateManager().getPrivateKey());
        peer.send(RawPeer.authRequest(null));
        SerializableEntity answer = peer.receive(5000);
        assertInstanceOf(MelResponse.class, answer, "test setup: the server did not answer the auth request");
        assertNotEquals(MelStrings.msg.STATE_ERR, ((MelResponse) answer).getState(), "test setup: the server refused the paired client");
        return peer;
    }

    @Disabled("fails on the old code: messages that do not fit are dropped or ignored and the connection stays open, see SOCKET_PROCESS_REWORK.md")
    @Test
    public void aDecisionWithoutARegistrationClosesTheConnection() throws Exception {
        try (RawPeer peer = stranger()) {
            peer.send(RawPeer.registerDecision(true));
            assertTrue(peer.hangsUpWithin(HANG_UP_MILLIS), "the server kept a connection open that started with a registration decision");
        }
        assertTrue(server.getCertificateManager().getAllCertificateDetails().isEmpty(), "the server knows the stranger now");
    }

    @Disabled("fails on the old code: messages that do not fit are dropped or ignored and the connection stays open, see SOCKET_PROCESS_REWORK.md")
    @Test
    public void somethingThatIsNoMessageClosesTheConnection() throws Exception {
        try (RawPeer peer = stranger()) {
            peer.sendUnparsable();
            assertTrue(peer.hangsUpWithin(HANG_UP_MILLIS), "the server kept a connection open that started with something that is no message");
        }
    }

    @Disabled("fails on the old code: the server stops reading but keeps the connection open, see SOCKET_PROCESS_REWORK.md")
    @Test
    public void aBlockOnANewConnectionClosesIt() throws Exception {
        try (RawPeer peer = stranger()) {
            peer.sendBlock();
            assertTrue(peer.hangsUpWithin(HANG_UP_MILLIS), "the server kept a connection open that started with a block");
        }
    }

    @Disabled("fails on the old code: the server stops reading but keeps the connection open, see SOCKET_PROCESS_REWORK.md")
    @Test
    public void aBlockOnANormalConnectionClosesIt() throws Exception {
        try (RawPeer peer = authenticatedClient()) {
            peer.sendBlock();
            assertTrue(peer.hangsUpWithin(HANG_UP_MILLIS), "the server kept a normal connection open that received a block");
        }
    }

    @Disabled("fails on the old code: messages that do not fit are dropped or ignored and the connection stays open, see SOCKET_PROCESS_REWORK.md")
    @Test
    public void aRegistrationOnANormalConnectionClosesIt() throws Exception {
        try (RawPeer peer = authenticatedClient()) {
            peer.send(RawPeer.registerRequest("CLIENT", client.getCertificateManager().getMyX509Certificate(),
                    client.getSettings().getPort(), client.getSettings().getDeliveryPort()));
            assertTrue(peer.hangsUpWithin(HANG_UP_MILLIS), "the server kept a normal connection open that received a registration request");
        }
    }

    @Disabled("fails on the old code: messages that do not fit are dropped or ignored and the connection stays open, see SOCKET_PROCESS_REWORK.md")
    @Test
    public void authenticatingTwiceClosesTheConnection() throws Exception {
        try (RawPeer peer = authenticatedClient()) {
            peer.send(RawPeer.authRequest(null));
            assertTrue(peer.hangsUpWithin(HANG_UP_MILLIS), "the server kept a normal connection open that received a second auth request");
        }
    }

    @Disabled("fails on the old code: messages that do not fit are dropped or ignored and the connection stays open, see SOCKET_PROCESS_REWORK.md")
    @Test
    public void somethingThatIsNoMessageClosesANormalConnection() throws Exception {
        try (RawPeer peer = authenticatedClient()) {
            peer.sendUnparsable();
            assertTrue(peer.hangsUpWithin(HANG_UP_MILLIS), "the server kept a normal connection open that received something that is no message");
        }
    }
}

package de.mel.auth.service;

import de.mel.auth.data.access.CertificateCreator;
import de.mel.auth.socket.FrameCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The wire format has limits. A header that breaks them closes the connection before anything is allocated.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class FrameLimitTest {
    @TempDir
    File tempDir;

    private TwoInstances instances;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, 28000, ACCEPT_ALL, ACCEPT_ALL);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    private RawPeer stranger() throws Exception {
        KeyPair keys = CertificateCreator.generateKeyPair(2048);
        return RawPeer.connect(instances.server.getSettings().getPort(), CertificateCreator.generateCertificate(keys, "stranger"), keys.getPrivate());
    }

    @Test
    public void aFrameAboveTheMaximumClosesTheConnection() throws Exception {
        try (RawPeer peer = stranger()) {
            peer.sendRawHeader(FrameCodec.MESSAGE, FrameCodec.MAX_FRAME + 1);
            assertTrue(peer.hangsUpWithin(5000), "the instance waits for a frame that it must not accept");
        }
    }

    @Test
    public void aNegativeLengthClosesTheConnection() throws Exception {
        try (RawPeer peer = stranger()) {
            peer.sendRawHeader(FrameCodec.MESSAGE, -5);
            assertTrue(peer.hangsUpWithin(5000));
        }
    }

    @Test
    public void anUnknownFrameTypeClosesTheConnection() throws Exception {
        try (RawPeer peer = stranger()) {
            peer.sendRawHeader((byte) 42, 3);
            assertTrue(peer.hangsUpWithin(5000));
        }
    }
}

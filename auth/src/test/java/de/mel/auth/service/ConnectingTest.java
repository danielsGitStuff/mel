package de.mel.auth.service;

import de.mel.auth.data.access.CertificateCreator;
import de.mel.auth.socket.MelAuthSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Connecting must not hang on partners that do not answer, and one bad client must not stop the others.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class ConnectingTest {
    @TempDir
    File tempDir;

    private TwoInstances instances;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, 32000, ACCEPT_ALL, ACCEPT_ALL);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    /**
     * The certificate delivery used to read the request in its accept thread: one client that says nothing stopped all the others.
     */
    @Test
    public void aSilentClientOnTheDeliveryPortDoesNotStopAPairing() throws Exception {
        try (Socket silent = new Socket()) {
            silent.connect(new InetSocketAddress("localhost", instances.server.getSettings().getDeliveryPort()), 2000);
            Thread.sleep(300);
            long start = System.nanoTime();
            instances.pair();
            assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 20, "pairing took too long");
        }
    }

    /**
     * 10.255.255.1 is not routed anywhere in practice: the connection attempt hangs until the timeout, or fails at once if the network says so.
     */
    @Test
    public void connectingToAnAddressThatDoesNotAnswerFailsWithinTheConnectTimeout() throws Exception {
        instances.client.setConnectTimeoutMs(1500);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        long start = System.nanoTime();
        instances.client.connect("10.255.255.1", 18999, 18998, false).onDone(p -> finished.countDown()).onError(e -> {
            failure.set(e);
            finished.countDown();
        });
        assertTrue(finished.await(15, TimeUnit.SECONDS), "connecting to an address that does not answer never came to an end");
        assertNotNull(failure.get());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 12_000);
    }

    /**
     * A client that connects and says nothing is dropped after the (short) timeout of the state NEW
     */
    @Test
    public void aConnectionThatNeverSaysAnythingIsClosed() throws Exception {
        instances.server.setStateTimeoutMs(1000);
        KeyPair keys = CertificateCreator.generateKeyPair(2048);
        try (RawPeer peer = RawPeer.connect(instances.server.getSettings().getPort(), CertificateCreator.generateCertificate(keys, "mute"), keys.getPrivate())) {
            assertTrue(peer.hangsUpWithin(8000), "the server keeps a connection open that never said anything");
        }
    }

    /**
     * Communications off (Android power saving) also closes the connections that are up
     */
    @Test
    public void shuttingDownCommunicationsClosesTheConnections() throws Exception {
        instances.pair();
        MelAuthService server = instances.server;
        MelAuthService client = instances.client;
        TwoInstances.connect(client, server);
        assertTrue(waitFor(() -> {
            try {
                return !openNormalConnections(client, server).isEmpty() && !openNormalConnections(server, client).isEmpty();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 10), "test setup: no connection");
        server.shutDownCommunications();
        assertTrue(waitFor(() -> {
            try {
                return openNormalConnections(client, server).isEmpty() && openNormalConnections(server, client).isEmpty();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 10), "connections are still open after the communications were shut down");
    }
}

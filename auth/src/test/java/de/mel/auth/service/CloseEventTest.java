package de.mel.auth.service;

import de.mel.auth.data.EmptyPayload;
import de.mel.auth.socket.CloseReason;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.val.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Closing a connection is an event in the queue of the socket: it happens once, after what was received before, and whoever waits
 * for an answer is told at once.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class CloseEventTest {
    private TwoInstances instances;
    private MelAuthService server;
    private MelAuthService client;

    @TempDir
    File tempDir;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, 27000, ACCEPT_ALL, ACCEPT_ALL);
        server = instances.server;
        client = instances.client;
        instances.pair();
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    /**
     * Nobody answers this request. It used to fail by its timeout of 30 seconds only.
     */
    @Test
    public void aPendingRequestFailsAtOnceWhenThePartnerCloses() throws Exception {
        MelTestService service = new MelTestService(server, new File(tempDir, "service"), 1L, "a service");
        server.registerMelService(service);
        grant(server, service, client);
        MelValidationProcess process = connect(client, server);
        Request<?> request = process.request(service.getUuid(), new EmptyPayload("no answer to this"));
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        request.onError(e -> {
            failure.set(e);
            failed.countDown();
        });
        assertTrue(waitFor(() -> {
            try {
                return !openNormalConnections(server, client).isEmpty();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 10), "server does not see the connection");
        Thread.sleep(300);
        assertEquals(1, failed.getCount(), "the request failed without anybody closing anything");
        long start = System.nanoTime();
        for (MelAuthSocket socket : openNormalConnections(server, client))
            socket.close(CloseReason.SHUTDOWN);
        assertTrue(failed.await(TwoInstances.TIMEOUT_SECONDS, TimeUnit.SECONDS), "the request was not failed after the partner closed");
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(millis < 10_000, "took " + millis + " ms");
        assertNotNull(failure.get());
    }

    @Test
    public void aRequestOnACloseSocketOfOurOwnFailsToo() throws Exception {
        MelTestService service = new MelTestService(server, new File(tempDir, "service"), 1L, "a service");
        server.registerMelService(service);
        grant(server, service, client);
        MelValidationProcess process = connect(client, server);
        Request<?> request = process.request(service.getUuid(), new EmptyPayload("no answer to this"));
        CountDownLatch failed = new CountDownLatch(1);
        request.onError(e -> failed.countDown());
        process.getMelAuthSocket().close(CloseReason.SHUTDOWN);
        assertTrue(failed.await(TwoInstances.TIMEOUT_SECONDS, TimeUnit.SECONDS), "the request was not failed after we closed");
    }

    @Test
    public void onIsolatedConnectionClosedIsCalledExactlyOnceOnEachSide() throws Exception {
        IsolationRecordingService serverService = new IsolationRecordingService(server, new File(tempDir, "server service"), "server service uuid");
        IsolationRecordingService clientService = new IsolationRecordingService(client, new File(tempDir, "client service"), "client service uuid");
        server.registerMelService(serverService);
        client.registerMelService(clientService);
        grant(server, serverService, client);
        Long serverCertId = idOf(server, client);
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<MelIsolatedProcess> clientSide = new AtomicReference<>();
        client.connectToService(TestIsolatedProcess.class, serverCertId, serverService.getUuid(), clientService.getUuid(), null, null, null)
                .onDone(p -> {
                    clientSide.set(p);
                    opened.countDown();
                });
        assertTrue(opened.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "isolated connection was not established");
        assertTrue(serverService.firstEstablished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        // the server hangs up
        serverService.established.get(0).stop();
        assertTrue(waitFor(() -> clientService.closedCalls.get() > 0 && serverService.closedCalls.get() > 0, 10), "close was not reported on both sides");
        // anything reported twice would show up shortly after (milliseconds over loopback)
        Thread.sleep(500);
        assertEquals(1, serverService.closedCalls.get(), "server side: onIsolatedConnectionClosed");
        assertEquals(1, clientService.closedCalls.get(), "client side: onIsolatedConnectionClosed");
    }
}

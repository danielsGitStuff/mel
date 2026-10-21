package de.mel.auth.service;

import de.mel.auth.data.MelRequest;
import de.mel.auth.data.ResponseException;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelSocket;
import de.mel.auth.socket.process.val.MelServicesPayload;
import de.mel.auth.socket.process.val.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Small things of the instance that the socket rework fixes on the side: request timeouts, the socket set, a lookup without connection.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class InstanceBookkeepingTest {
    private static final int PORT_BASE = 26000;

    @TempDir
    File tempDir;

    private TwoInstances instances;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, PORT_BASE, ACCEPT_ALL, ACCEPT_ALL);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    @SuppressWarnings("unchecked")
    private static List<MelSocket> socketsOf(MelAuthService instance) throws Exception {
        Field field = MelAuthServiceImpl.class.getDeclaredField("sockets");
        field.setAccessible(true);
        return new ArrayList<>((Set<MelSocket>) field.get(instance));
    }

    @Test
    public void aRequestWithoutAnswerIsRejectedAfterTheTimeoutOfTheInstance() throws Exception {
        MelAuthService server = instances.server;
        server.setRequestTimeoutMs(300);
        MelRequest request = new MelRequest("service", "intent");
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch rejected = new CountDownLatch(1);
        request.getAnswerDeferred().fail(e -> {
            failure.set(e);
            rejected.countDown();
        });
        request.startTimeout(server);
        assertTrue(rejected.await(5, TimeUnit.SECONDS), "the request was not rejected");
        assertTrue(failure.get() instanceof ResponseException);
    }

    @Test
    public void aStoppedTimeoutDoesNotRejectAndRestartingPostponesIt() throws Exception {
        MelAuthService server = instances.server;
        server.setRequestTimeoutMs(600);
        MelRequest stopped = new MelRequest("service", "intent");
        MelRequest restarted = new MelRequest("service", "intent");
        stopped.startTimeout(server);
        restarted.startTimeout(server);
        Thread.sleep(300);
        stopped.stopTimeout();
        restarted.startTimeout(server);
        Thread.sleep(400);
        // 700 ms since the first start: the restarted one would have been rejected without the restart
        assertTrue(restarted.getAnswerDeferred().isPending(), "restart did not postpone the timeout");
        assertTrue(waitFor(() -> restarted.getAnswerDeferred().isRejected(), 5), "restarted request was never rejected");
        assertTrue(stopped.getAnswerDeferred().isPending(), "a stopped timeout must not reject");
    }

    @Test
    public void allowedServicesOfAPartnerWithoutConnectionIsRejectedNotAnException() throws Exception {
        Request<MelServicesPayload> request = instances.server.getAllowedServices(4711L);
        assertTrue(request.isRejected());
    }

    @Test
    public void closedSocketsAreRemovedFromTheInstance() throws Exception {
        instances.pair();
        // pairing used plain sockets (certificate retrieval and delivery) and a register socket: none of them is needed afterwards
        for (MelAuthService instance : new MelAuthService[]{instances.server, instances.client}) {
            boolean clean = waitFor(() -> {
                try {
                    return socketsOf(instance).stream().noneMatch(s -> s.isStopped() || (s.getClass() == MelSocket.class));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 10);
            assertTrue(clean, instance.getName() + " still holds closed sockets: " + socketsOf(instance));
        }
    }
}

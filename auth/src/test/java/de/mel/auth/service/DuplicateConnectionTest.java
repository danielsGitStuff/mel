package de.mel.auth.service;

import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelValidationProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Two paired instances have exactly one normal connection between them, the same one on both sides, however they got there.
 * The rule (see SOCKET_PROCESS_REWORK.md): different initiators -> the one initiated by the lower certificate hash wins;
 * same initiator -> the newer one wins.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class DuplicateConnectionTest {
    private static final AtomicInteger nextPortBase = new AtomicInteger(23000);

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
        instances.pair();
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    /**
     * Waits until each side has exactly one open normal connection to the other and both are the two ends of the same one.
     */
    private void assertExactlyOneConnectionSurvives() throws Exception {
        boolean settled = waitFor(() -> {
            try {
                List<MelAuthSocket> atServer = openNormalConnections(server, client);
                List<MelAuthSocket> atClient = openNormalConnections(client, server);
                return atServer.size() == 1 && atClient.size() == 1 && sameConnection(atServer.get(0), atClient.get(0));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 15);
        List<MelAuthSocket> atServer = openNormalConnections(server, client);
        List<MelAuthSocket> atClient = openNormalConnections(client, server);
        assertTrue(settled, "expected one shared connection, server has " + atServer.size() + ", client has " + atClient.size()
                + (atServer.size() == 1 && atClient.size() == 1 ? " (different ones)" : ""));
        // and it is the one each side uses
        assertSame(atServer.get(0), environmentOf(server).getValidationProcess(idOf(client, server)).getMelAuthSocket());
        assertSame(atClient.get(0), environmentOf(client).getValidationProcess(idOf(server, client)).getMelAuthSocket());
    }

    @Test
    public void oneSideConnects() throws Exception {
        connect(client, server);
        assertExactlyOneConnectionSurvives();
    }

    /**
     * Both want to talk to each other at the same moment, e.g. both have something to push.
     */
    @RepeatedTest(5)
    public void bothSidesConnectAtTheSameMoment() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Long serverIdAtClient = idOf(server, client);
        Long clientIdAtServer = idOf(client, server);
        for (Runnable connecting : new Runnable[]{
                () -> {
                    try {
                        start.await();
                        client.connect(serverIdAtClient).onDone(a -> done.countDown()).onError(e -> done.countDown());
                    } catch (Exception e) {
                        done.countDown();
                    }
                },
                () -> {
                    try {
                        start.await();
                        server.connect(clientIdAtServer).onDone(a -> done.countDown()).onError(e -> done.countDown());
                    } catch (Exception e) {
                        done.countDown();
                    }
                }}) {
            new Thread(connecting).start();
        }
        start.countDown();
        // one of the two may be refused, but both have to come to an end
        assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "a connection attempt never came to an end");
        assertExactlyOneConnectionSurvives();
    }

    /**
     * The client gave up its connection (as after a network change), the server has not noticed yet (TCP may take minutes).
     * The client connects again: the new connection has to win, otherwise the client is locked out until the server notices.
     */
    @Test
    public void reconnectWhileThePartnerStillHoldsTheOldConnection() throws Exception {
        MelValidationProcess old = connect(client, server);
        // the client forgets it, the socket stays open
        environmentOf(client).removeValidationProcess(old.getMelAuthSocket());
        assertFalse(client.isConnectedTo(idOf(server, client)), "test setup: the client should have forgotten the connection");

        MelValidationProcess renewed = connect(client, server);
        assertNotSame(old, renewed, "the client did not connect again");
        assertExactlyOneConnectionSurvives();
        assertSame(renewed.getMelAuthSocket(), openNormalConnections(client, server).get(0), "the old connection survived, not the new one");
    }
}

package de.mel.auth.service;

import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
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
 * File transfers open several isolated connections between the same two services. None may get in the way of another.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class SeveralIsolatedConnectionsTest {
    @TempDir
    File tempDir;

    private TwoInstances instances;
    private MelAuthService server;
    private MelAuthService client;
    private IsolationRecordingService serverService;
    private IsolationRecordingService clientService;
    private Long serverIdAtClient;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, 30000, ACCEPT_ALL, ACCEPT_ALL);
        server = instances.server;
        client = instances.client;
        instances.pair();
        serverService = new IsolationRecordingService(server, new File(tempDir, "server service"), "server service uuid");
        clientService = new IsolationRecordingService(client, new File(tempDir, "client service"), "client service uuid");
        server.registerMelService(serverService);
        client.registerMelService(clientService);
        grant(server, serverService, client);
        serverIdAtClient = idOf(server, client);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    private TestIsolatedProcess open() throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<MelIsolatedProcess> process = new AtomicReference<>();
        client.connectToService(TestIsolatedProcess.class, serverIdAtClient, serverService.getUuid(), clientService.getUuid(), null, null, null)
                .onDone(p -> {
                    process.set(p);
                    opened.countDown();
                });
        assertTrue(opened.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "isolated connection was not established");
        return (TestIsolatedProcess) process.get();
    }

    private TestIsolatedProcess partnerOf(TestIsolatedProcess mine) throws Exception {
        assertTrue(waitFor(() -> serverService.established.stream().anyMatch(p -> p.getIsolatedUuid().equals(mine.getIsolatedUuid())), 10),
                "the server does not know the isolated connection");
        return (TestIsolatedProcess) serverService.established.stream().filter(p -> p.getIsolatedUuid().equals(mine.getIsolatedUuid())).findFirst().get();
    }

    @Test
    public void twoIsolatedConnectionsBetweenTheSameServicesAreBothUsable() throws Exception {
        TestIsolatedProcess first = open();
        TestIsolatedProcess second = open();
        assertNotSame(first, second);
        assertNotEquals(first.getIsolatedUuid(), second.getIsolatedUuid());
        TestIsolatedProcess firstAtServer = partnerOf(first);
        TestIsolatedProcess secondAtServer = partnerOf(second);
        assertNotSame(firstAtServer, secondAtServer);

        first.sendBlock(new byte[]{1, 1, 1});
        second.sendBlock(new byte[]{2, 2});
        secondAtServer.sendBlock(new byte[]{3});
        assertTrue(waitFor(() -> firstAtServer.received.size() == 1 && secondAtServer.received.size() == 1 && second.received.size() == 1, 10),
                "blocks did not arrive: " + firstAtServer.received.size() + "/" + secondAtServer.received.size() + "/" + second.received.size());
        assertArrayEquals(new byte[]{1, 1, 1}, firstAtServer.received.get(0), "a block of the first connection arrived on the wrong one");
        assertArrayEquals(new byte[]{2, 2}, secondAtServer.received.get(0));
        assertArrayEquals(new byte[]{3}, second.received.get(0));
        assertTrue(first.received.isEmpty());
    }

    @Test
    public void closingOneIsolatedConnectionLeavesTheOtherOne() throws Exception {
        TestIsolatedProcess first = open();
        TestIsolatedProcess second = open();
        TestIsolatedProcess firstAtServer = partnerOf(first);
        TestIsolatedProcess secondAtServer = partnerOf(second);

        first.stop();
        assertTrue(waitFor(() -> !firstAtServer.isOpen() && !first.isOpen(), 10), "the first connection was not closed on both sides");
        assertTrue(waitFor(() -> serverService.closedCalls.get() == 1 && clientService.closedCalls.get() == 1, 10));
        Thread.sleep(500);
        assertTrue(second.isOpen());
        assertTrue(secondAtServer.isOpen());
        assertEquals(1, serverService.closedCalls.get(), "more than the closed connection was reported");

        second.sendBlock(new byte[]{9, 9});
        assertTrue(waitFor(() -> secondAtServer.received.size() == 1, 10), "the remaining connection is not usable");
        // and the service still hands out the one that is left
        AtomicReference<MelIsolatedProcess> again = new AtomicReference<>();
        serverService.getIsolatedProcess(TestIsolatedProcess.class, idOf(client, server), clientService.getUuid()).onDone(again::set);
        assertTrue(waitFor(() -> again.get() != null, 5));
        assertSame(secondAtServer, again.get());
    }

    private MelIsolatedProcess getIsolated(IsolationRecordingService from, MelAuthService owner, Long partnerId, String partnerServiceUuid, int seconds) throws Exception {
        AtomicReference<MelIsolatedProcess> got = new AtomicReference<>();
        from.getIsolatedProcess(TestIsolatedProcess.class, partnerId, partnerServiceUuid).onDone(got::set);
        waitFor(() -> got.get() != null, seconds);
        return got.get();
    }

    @Test
    public void getIsolatedProcessReusesAnOpenOne() throws Exception {
        MelIsolatedProcess first = getIsolated(clientService, client, serverIdAtClient, serverService.getUuid(), 10);
        assertNotNull(first);
        MelIsolatedProcess again = getIsolated(clientService, client, serverIdAtClient, serverService.getUuid(), 10);
        assertSame(first, again);
        // and the other side finds the same connection, not a new one
        assertTrue(waitFor(() -> serverService.established.size() == 1, 10));
        MelIsolatedProcess atServer = getIsolated(serverService, server, idOf(client, server), clientService.getUuid(), 10);
        assertSame(serverService.established.get(0), atServer);
        assertEquals(1, serverService.established.size());
    }

    @Test
    public void getIsolatedProcessOpensANewOneAfterTheOldOneWasClosed() throws Exception {
        MelIsolatedProcess first = getIsolated(clientService, client, serverIdAtClient, serverService.getUuid(), 10);
        assertNotNull(first);
        first.stop();
        assertTrue(waitFor(() -> !first.isOpen() && clientService.closedCalls.get() == 1, 10));
        MelIsolatedProcess second = getIsolated(clientService, client, serverIdAtClient, serverService.getUuid(), 10);
        assertNotNull(second, "no new isolated connection was opened");
        assertNotSame(first, second);
        assertTrue(second.isOpen());
    }

    @Test
    public void getIsolatedProcessTriesAgainAfterAFailedAttempt() throws Exception {
        IsolationRecordingService other = new IsolationRecordingService(server, new File(tempDir, "other service"), "other service uuid");
        server.registerMelService(other);
        AtomicReference<Exception> failure = new AtomicReference<>();
        clientService.getIsolatedProcess(TestIsolatedProcess.class, serverIdAtClient, other.getUuid()).onError(failure::set);
        assertTrue(waitFor(() -> failure.get() != null || other.firstEstablished.getCount() == 0, 15) && other.established.isEmpty(),
                "the connection to a service without access did not fail");
        // now the client may use that service: the failed attempt must not be handed out again
        grant(server, other, client);
        MelIsolatedProcess second = getIsolated(clientService, client, serverIdAtClient, other.getUuid(), 15);
        assertNotNull(second, "no second attempt was made after the first one failed");
        assertTrue(second.isOpen());
    }
}

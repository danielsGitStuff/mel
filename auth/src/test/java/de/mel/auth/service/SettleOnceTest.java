package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A promise is settled once. Settling a settled one throws, and most of the code that does so is inside a callback that logs and carries on,
 * so a double settle shows up only as a stack trace on stderr. This runs the flows that settle jobs and looks for such traces.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class SettleOnceTest {
    @TempDir
    File tempDir;

    private static PrintStream tee(PrintStream original, ByteArrayOutputStream captured) {
        return new PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
                original.write(b);
                synchronized (captured) {
                    captured.write(b);
                }
            }
        }, true);
    }

    @Test
    public void connectJobsAreSettledOnceInTheFlowsThatSettleThem() throws Exception {
        // stack traces go to stderr, the log lines that carry exceptions go to stdout
        PrintStream originalErr = System.err;
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(tee(originalErr, captured));
        System.setOut(tee(originalOut, captured));
        try (TwoInstances instances = new TwoInstances(tempDir, 33500, ACCEPT_ALL, ACCEPT_ALL)) {
            instances.pair();
            // a connection that is refused: the partner does not know us anymore
            closeNormalConnections(instances.server, instances.client);
            // both connect at the same moment
            CountDownLatch bothDone = new CountDownLatch(2);
            Long serverIdAtClient = idOf(instances.server, instances.client);
            Long clientIdAtServer = idOf(instances.client, instances.server);
            new Thread(() -> {
                try {
                    instances.client.connect(serverIdAtClient).onDone(a -> bothDone.countDown()).onError(e -> bothDone.countDown());
                } catch (Exception e) {
                    bothDone.countDown();
                }
            }).start();
            new Thread(() -> {
                try {
                    instances.server.connect(clientIdAtServer).onDone(a -> bothDone.countDown()).onError(e -> bothDone.countDown());
                } catch (Exception e) {
                    bothDone.countDown();
                }
            }).start();
            assertTrue(bothDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            // a connection to nowhere
            instances.client.setConnectTimeoutMs(500);
            CountDownLatch nowhere = new CountDownLatch(1);
            instances.client.connect("10.255.255.1", 18999, 18998, false).onDone(a -> nowhere.countDown()).onError(e -> nowhere.countDown());
            assertTrue(nowhere.await(TwoInstances.TIMEOUT_SECONDS, TimeUnit.SECONDS));
            // a connection that is cut while it is being made
            closeNormalConnections(instances.server, instances.client);
            CountDownLatch cut = new CountDownLatch(1);
            instances.client.connect(serverIdAtClient).onDone(a -> cut.countDown()).onError(e -> cut.countDown());
            instances.server.shutDownCommunications();
            assertTrue(cut.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Thread.sleep(500);
        } finally {
            System.setErr(originalErr);
            System.setOut(originalOut);
        }
        String log;
        synchronized (captured) {
            log = captured.toString();
        }
        assertFalse(log.contains("already finished"), "something settled a job twice:\n" + log);
        assertFalse(log.contains("AlreadyResolved"), "something settled a promise twice:\n" + log);
    }
}

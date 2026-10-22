package de.mel.auth.service;

import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.MelValidationProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Several threads send on the same connection at once. Every message has to arrive whole, however long it is.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class ConcurrentSendTest {
    private static final AtomicInteger nextPortBase = new AtomicInteger(22000);

    /**
     * a message of any length
     */
    public static class TextPayload extends ServicePayload {
        private String text;

        public String getText() {
            return text;
        }

        public TextPayload setText(String text) {
            this.text = text;
            return this;
        }
    }

    /**
     * remembers the text of every message it is handed
     */
    static class ReceivingService extends MelTestService {
        final List<String> received = new CopyOnWriteArrayList<>();

        ReceivingService(MelAuthService melAuthService, File workingDirectory) {
            super(melAuthService, workingDirectory, 1L, "receiving service");
        }

        @Override
        public void handleMessage(ServicePayload payload, Certificate partnerCertificate) {
            if (payload instanceof TextPayload text)
                received.add(text.getText());
        }
    }

    @TempDir
    File tempDir;

    private TwoInstances instances;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, nextPortBase.getAndAdd(10), ACCEPT_ALL, ACCEPT_ALL);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    /**
     * Long messages are written in several pieces. The pieces of two messages must not end up mixed on the wire.
     */
    @Test
    public void longMessagesFromSeveralThreadsArriveWhole() throws Exception {
        sendAndCheck(4, 5);
    }

    /**
     * the same without concurrency: shows that the setup works and long messages as such do
     */
    @Test
    public void longMessagesFromOneThreadArriveWhole() throws Exception {
        sendAndCheck(1, 5);
    }

    /**
     * writeUTF limited bytes, not characters: about 1000 characters of CJK or umlauts exceeded it and the message was lost
     */
    @Test
    public void longMessagesWithManyNonAsciiCharactersArriveWhole() throws Exception {
        sendAndCheck(2, 3, 100_000, "\u00e4\u00f6\u00fc\u4e2d\u6587\ud83d\ude00");
    }

    private void sendAndCheck(int threads, int perThread) throws Exception {
        sendAndCheck(threads, perThread, 200_000, null);
    }

    private void sendAndCheck(int threads, int perThread, int length, String fillers) throws Exception {
        MelAuthService server = instances.server;
        MelAuthService client = instances.client;
        instances.pair();
        ReceivingService receiver = new ReceivingService(server, new File(tempDir, "receiver"));
        server.registerMelService(receiver);
        grant(server, receiver, client);
        MelValidationProcess connection = connect(client, server);

        // several times the length that fits into one piece
        Set<String> sent = new HashSet<>();
        List<List<String>> work = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            List<String> mine = new ArrayList<>();
            for (int m = 0; m < perThread; m++) {
                String id = "message " + t + "/" + m + ":";
                String filler = fillers == null ? String.valueOf((char) ('a' + (t * perThread + m) % 26)) : fillers.substring((t * perThread + m) % 3, (t * perThread + m) % 3 + 1);
                String text = id + filler.repeat(length);
                mine.add(text);
                sent.add(text);
            }
            work.add(mine);
        }

        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        List<Thread> senders = new ArrayList<>();
        for (List<String> mine : work) {
            Thread sender = new Thread(() -> {
                try {
                    start.await();
                    for (String text : mine)
                        connection.message(receiver.getUuid(), new TextPayload().setText(text));
                } catch (Exception e) {
                    failure.set(e);
                }
            });
            sender.start();
            senders.add(sender);
        }
        start.countDown();
        for (Thread sender : senders)
            sender.join();
        assertNull(failure.get(), "sending failed");

        int expected = threads * perThread;
        boolean all = waitFor(() -> receiver.received.size() >= expected, TwoInstances.TIMEOUT_SECONDS);
        assertTrue(all, "only " + receiver.received.size() + " of " + expected + " messages arrived");
        assertEquals(sent, new HashSet<>(receiver.received), "messages arrived changed");
    }
}

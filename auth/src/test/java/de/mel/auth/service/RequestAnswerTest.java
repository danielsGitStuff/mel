package de.mel.auth.service;

import de.mel.auth.data.ResponseException;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.MelValidationProcess;
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

import static de.mel.auth.service.ConcurrentSendTest.TextPayload;
import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Requests over a real connection: the answer resolves the request, an error answer rejects it, a request nobody answers is rejected
 * by its timeout, and a service that throws does not cost the connection.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class RequestAnswerTest {
    /**
     * answers according to the intent of the request
     */
    static class AnsweringService extends MelTestService {
        AnsweringService(MelAuthService melAuthService, File workingDirectory) {
            super(melAuthService, workingDirectory, 1L, "answering service");
        }

        @Override
        public void handleRequest(Request request) throws Exception {
            String intent = request.getPayload() == null ? "" : ((TextPayload) request.getPayload()).getText();
            switch (intent) {
                case "echo":
                    request.resolveOk(new TextPayload().setText("echo of " + intent));
                    break;
                case "refuse":
                    request.resolveError(new ResponseException("not for you"));
                    break;
                case "throw":
                    throw new IllegalStateException("the service is broken");
                default:
                    // "silent": never answers
                    break;
            }
        }
    }

    @TempDir
    File tempDir;

    private TwoInstances instances;
    private MelValidationProcess connection;
    private AnsweringService service;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, 33000, ACCEPT_ALL, ACCEPT_ALL);
        instances.pair();
        service = new AnsweringService(instances.server, new File(tempDir, "service"));
        instances.server.registerMelService(service);
        grant(instances.server, service, instances.client);
        connection = connect(instances.client, instances.server);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    private static class Outcome {
        final CountDownLatch settled = new CountDownLatch(1);
        final AtomicReference<ServicePayload> answer = new AtomicReference<>();
        final AtomicReference<Exception> failure = new AtomicReference<>();
    }

    private Outcome ask(String what) throws Exception {
        Outcome outcome = new Outcome();
        connection.request(service.getUuid(), new TextPayload().setText(what)).onDone(answer -> {
            outcome.answer.set((ServicePayload) answer);
            outcome.settled.countDown();
        }).onError(e -> {
            outcome.failure.set((Exception) e);
            outcome.settled.countDown();
        });
        return outcome;
    }

    @Test
    public void anAnsweredRequestResolvesWithThePayloadOfTheAnswer() throws Exception {
        Outcome outcome = ask("echo");
        assertTrue(outcome.settled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNull(outcome.failure.get());
        assertEquals("echo of echo", ((TextPayload) outcome.answer.get()).getText());
    }

    @Test
    public void anErrorAnswerRejectsWithTheExceptionThatWasSent() throws Exception {
        Outcome outcome = ask("refuse");
        assertTrue(outcome.settled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNull(outcome.answer.get());
        assertInstanceOf(ResponseException.class, outcome.failure.get());
        assertEquals("not for you", outcome.failure.get().getMessage());
    }

    @Test
    public void aServiceThatThrowsAnswersWithAnErrorAndTheConnectionStays() throws Exception {
        Outcome thrown = ask("throw");
        assertTrue(thrown.settled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNotNull(thrown.failure.get());
        // the next request on the same connection is answered as usual
        Outcome next = ask("echo");
        assertTrue(next.settled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("echo of echo", ((TextPayload) next.answer.get()).getText());
        assertFalse(connection.isClosed());
    }

    @Test
    public void aRequestWithoutAnswerIsRejectedByItsTimeout() throws Exception {
        instances.client.setRequestTimeoutMs(500);
        Outcome outcome = ask("silent");
        assertTrue(outcome.settled.await(TwoInstances.TIMEOUT_SECONDS, TimeUnit.SECONDS), "a request nobody answers was never rejected");
        assertNull(outcome.answer.get());
        assertNotNull(outcome.failure.get());
        // and only that request: the connection is fine
        instances.client.setRequestTimeoutMs(30_000);
        Outcome next = ask("echo");
        assertTrue(next.settled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNull(next.failure.get());
    }

    @Test
    public void callbacksRunOnTheThreadThatSettles() throws Exception {
        Request<?> request = connection.request(service.getUuid(), new TextPayload().setText("echo"));
        CountDownLatch called = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();
        request.onDone(answer -> {
            thread.set(Thread.currentThread().getName());
            called.countDown();
        });
        assertTrue(called.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread().getName(), thread.get(), "the answer is handled by the thread of the socket, not by the one that asked");
        // registered after the answer: at once, on this thread
        AtomicReference<String> late = new AtomicReference<>();
        request.onDone(answer -> late.set(Thread.currentThread().getName()));
        assertEquals(Thread.currentThread().getName(), late.get());
    }
}

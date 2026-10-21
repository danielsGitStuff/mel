package de.mel.auth.jobs;

import de.mel.auth.data.MelRequest;
import de.mel.auth.data.ResponseException;
import de.mel.auth.data.EmptyPayload;
import de.mel.auth.socket.process.val.Request;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the code relies on when it uses requests and jobs as promises: callbacks run on the thread that settles, or at once on the thread
 * that registers them if the promise is settled already; a promise is settled once, settling it again is a bug that throws.
 */
public class PromiseSemanticsTest {

    private Thread settleInOtherThread(Runnable settle) throws InterruptedException {
        Thread thread = new Thread(settle, "settling thread");
        thread.start();
        thread.join();
        return thread;
    }

    @Test
    public void aCallbackRunsOnTheThreadThatSettlesTheRequest() throws Exception {
        Request<EmptyPayload> request = new Request<>();
        AtomicReference<String> ranOn = new AtomicReference<>();
        request.done(payload -> ranOn.set(Thread.currentThread().getName()));
        settleInOtherThread(() -> request.resolve(new EmptyPayload("x")));
        assertEquals("settling thread", ranOn.get());
    }

    @Test
    public void aFailureCallbackRunsOnTheThreadThatRejectsTheRequest() throws Exception {
        Request<EmptyPayload> request = new Request<>();
        AtomicReference<String> ranOn = new AtomicReference<>();
        request.fail(e -> ranOn.set(Thread.currentThread().getName()));
        settleInOtherThread(() -> request.reject(new ResponseException("no")));
        assertEquals("settling thread", ranOn.get());
    }

    @Test
    public void aCallbackRegisteredAfterwardsRunsAtOnceOnTheRegisteringThread() throws Exception {
        Request<EmptyPayload> request = new Request<>();
        request.resolve(new EmptyPayload("x"));
        AtomicReference<String> ranOn = new AtomicReference<>();
        request.done(payload -> ranOn.set(Thread.currentThread().getName()));
        assertEquals(Thread.currentThread().getName(), ranOn.get());
    }

    @Test
    public void onlyTheMatchingCallbackRuns() {
        Request<EmptyPayload> request = new Request<>();
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        AtomicReference<Boolean> failed = new AtomicReference<>(false);
        request.done(p -> done.set(true)).fail(e -> failed.set(true));
        request.reject(new ResponseException("no"));
        assertFalse(done.get());
        assertTrue(failed.get());
    }

    @Test
    public void aRequestIsSettledOnce() {
        Request<EmptyPayload> request = new Request<>();
        request.resolve(new EmptyPayload("x"));
        assertThrows(RuntimeException.class, () -> request.resolve(new EmptyPayload("y")));
        assertThrows(RuntimeException.class, () -> request.reject(new ResponseException("too late")));
        assertTrue(request.isResolved());
    }

    @Test
    public void aRejectedRequestStaysRejected() {
        Request<EmptyPayload> request = new Request<>();
        request.reject(new ResponseException("no"));
        assertThrows(RuntimeException.class, () -> request.resolve(new EmptyPayload("y")));
        assertTrue(request.isRejected());
    }

    @Test
    public void theAnswerOfARequestMessageIsSettledOnce() {
        MelRequest request = new MelRequest("service", "intent");
        request.getAnswerDeferred().reject(new ResponseException("no"));
        assertThrows(RuntimeException.class, () -> request.getAnswerDeferred().reject(new ResponseException("again")));
    }

    @Test
    public void aConnectJobIsSettledOnce() {
        ConnectJob job = new ConnectJob(1L, "localhost", 1, 2, false);
        assertTrue(job.isPending());
        job.resolve(null);
        assertTrue(job.isResolved());
        assertThrows(RuntimeException.class, () -> job.reject(new Exception("too late")));
        assertThrows(RuntimeException.class, () -> job.resolve(null));
    }

    @Test
    public void anIsolatedConnectJobIsSettledOnce() {
        IsolatedConnectJob<de.mel.auth.service.TestIsolatedProcess> job = new IsolatedConnectJob<>(1L, "localhost", 1, 2, "remote", "own", de.mel.auth.service.TestIsolatedProcess.class);
        job.reject(new Exception("no"));
        assertTrue(job.isRejected());
        assertThrows(RuntimeException.class, () -> job.resolve(null));
    }
}

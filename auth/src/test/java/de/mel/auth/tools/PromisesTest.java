package de.mel.auth.tools;

import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two ways Mel waits for several promises. Their contracts are what the code relies on, whatever the promises are made of:
 * {@link Promise#fusedAnd} where everything has to work out (booting), {@link Promises#allSettled} where nothing may stop the others (shutting down).
 */
public class PromisesTest {

    @Test
    public void waitingForNothingResolvesAtOnce() {
        assertTrue(Promise.fusedAnd().hasResult());
        assertTrue(Promise.fusedAnd(new Promise[0]).hasResult());
    }

    @Test
    public void waitingForPromisesThatAreSettledAlreadyStillCallsBack() {
        PromiseImpl<String, Exception> a = PromiseImpl.withoutThrowingOnException();
        PromiseImpl<String, Exception> b = PromiseImpl.withoutThrowingOnException();
        a.resolveOk("a");
        b.resolveOk("b");
        AtomicInteger called = new AtomicInteger();
        Promise.fusedAnd(a, b).onDone(r -> called.incrementAndGet());
        assertEquals(1, called.get());
    }

    @Test
    public void waitingForSeveralResolvesWhenTheLastOneIsDone() {
        PromiseImpl<String, Exception> a = PromiseImpl.withoutThrowingOnException();
        PromiseImpl<String, Exception> b = PromiseImpl.withoutThrowingOnException();
        Promise<?, ?> all = Promise.fusedAnd(a, b);
        a.resolveOk("a");
        assertTrue(all.isPending());
        b.resolveOk("b");
        assertTrue(all.hasResult());
    }

    @Test
    public void waitingForSeveralFailsAtTheFirstFailure() {
        PromiseImpl<String, Exception> a = PromiseImpl.withoutThrowingOnException();
        PromiseImpl<String, Exception> b = PromiseImpl.withoutThrowingOnException();
        Promise<?, ?> all = Promise.fusedAnd(a, b);
        a.resolveError(new Exception("a failed"));
        assertTrue(all.hasError(), "fail fast: the other one is still pending");
        assertTrue(b.isPending());
        b.resolveOk("late");
        assertTrue(all.hasError());
    }

    @Test
    public void shutDownResolvesWithoutPromises() {
        assertTrue(Promises.allSettled().hasResult());
    }

    @Test
    public void shutDownSkipsNulls() {
        // onShutDown() implementations return null now and then
        assertTrue(Promises.allSettled((Promise<?, ?>) null, null).hasResult());
    }

    @Test
    public void shutDownResolvesAlthoughAPromiseFails() {
        PromiseImpl<Void, Void> failing = PromiseImpl.withoutThrowingOnException();
        PromiseImpl<Void, Void> fine = PromiseImpl.withoutThrowingOnException();
        Promise<Void, Void> done = Promises.allSettled(failing, fine, null);
        assertTrue(done.isPending());
        failing.resolveError(null);
        fine.resolveOk(null);
        assertTrue(done.hasResult(), "a shutdown that fails somewhere is still a shutdown");
    }

    @Test
    public void shutDownWaitsForTheLastOne() {
        PromiseImpl<Void, Void> a = PromiseImpl.withoutThrowingOnException();
        PromiseImpl<Void, Void> b = PromiseImpl.withoutThrowingOnException();
        Promise<Void, Void> done = Promises.allSettled(a, b);
        a.resolveOk(null);
        assertTrue(done.isPending());
        b.resolveOk(null);
        assertTrue(done.hasResult());
    }
}

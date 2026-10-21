package de.mel.auth.tools;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two ways Mel waits for several promises. Their contracts are what the code relies on, whatever the promises are made of.
 */
public class DeferredManagersTest {

    @Test
    public void waitingForNothingResolvesAtOnce() {
        Promise<?, ?, ?> result = new MelDeferredManager().when(new Promise[0]);
        assertTrue(result.isResolved());
    }

    @Test
    public void waitingForPromisesThatAreSettledAlreadyStillCallsBack() {
        DeferredObject<String, Exception, Void> a = new DeferredObject<>();
        DeferredObject<String, Exception, Void> b = new DeferredObject<>();
        a.resolve("a");
        b.resolve("b");
        AtomicInteger called = new AtomicInteger();
        new MelDeferredManager().when(a, b).done(r -> called.incrementAndGet());
        assertEquals(1, called.get());
    }

    @Test
    public void waitingForSeveralResolvesWhenTheLastOneIsDone() {
        DeferredObject<String, Exception, Void> a = new DeferredObject<>();
        DeferredObject<String, Exception, Void> b = new DeferredObject<>();
        Promise<?, ?, ?> all = new MelDeferredManager().when(a, b);
        a.resolve("a");
        assertTrue(all.isPending());
        b.resolve("b");
        assertTrue(all.isResolved());
    }

    @Test
    public void waitingForSeveralRejectsAtTheFirstFailure() {
        DeferredObject<String, Exception, Void> a = new DeferredObject<>();
        DeferredObject<String, Exception, Void> b = new DeferredObject<>();
        Promise<?, ?, ?> all = new MelDeferredManager().when(a, b);
        a.reject(new Exception("a failed"));
        assertTrue(all.isRejected(), "fail fast: the other one is still pending");
        assertTrue(b.isPending());
        b.resolve("late");
        assertTrue(all.isRejected());
    }

    @Test
    public void shutDownManagerResolvesWithoutPromises() {
        assertTrue(new ShutDownDeferredManager().digest().isResolved());
    }

    @Test
    public void shutDownManagerSkipsNulls() {
        // onShutDown() implementations return null now and then
        assertTrue(new ShutDownDeferredManager().when((Promise) null, null).digest().isResolved());
    }

    @Test
    public void shutDownManagerResolvesAlthoughAPromiseRejects() {
        DeferredObject<Void, Void, Void> failing = new DeferredObject<>();
        DeferredObject<Void, Void, Void> fine = new DeferredObject<>();
        DeferredObject<Void, Void, Void> digest = new ShutDownDeferredManager().when(failing, fine, null).digest();
        assertTrue(digest.isPending());
        failing.reject(null);
        fine.resolve(null);
        assertTrue(digest.isResolved(), "a shutdown that fails somewhere is still a shutdown");
    }

    @Test
    public void shutDownManagerWaitsForTheLastOne() {
        DeferredObject<Void, Void, Void> a = new DeferredObject<>();
        DeferredObject<Void, Void, Void> b = new DeferredObject<>();
        DeferredObject<Void, Void, Void> digest = new ShutDownDeferredManager().when(a, b).digest();
        a.resolve(null);
        assertTrue(digest.isPending());
        b.resolve(null);
        assertTrue(digest.isResolved());
    }
}

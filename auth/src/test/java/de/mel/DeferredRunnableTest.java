package de.mel;

import org.jdeferred.Promise;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class DeferredRunnableTest {
    private static class Counting extends DeferredRunnable {
        final AtomicInteger entered = new AtomicInteger();

        @Override
        public Promise<Void, Void, Void> onShutDown() {
            return DeferredRunnable.ResolvedDeferredObject();
        }

        @Override
        public void runImpl() {
            entered.incrementAndGet();
        }

        @Override
        public String getRunnableName() {
            return "counting";
        }
    }

    @Test
    public void aStopBeforeTheThreadStartedIsNotLost() throws Exception {
        Counting runnable = new Counting();
        runnable.stop();
        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();
        assertTrue(runnable.isStopped(), "run() must not take the stop back");
        assertEquals(0, runnable.entered.get(), "a runnable that was stopped before it started must not run");
    }

    @Test
    public void shutDownBeforeTheThreadStartedIsNotLost() throws Exception {
        Counting runnable = new Counting();
        runnable.shutDown();
        runnable.run();
        assertTrue(runnable.isStopped());
        assertEquals(0, runnable.entered.get());
    }

    @Test
    public void restartMakesAStoppedRunnableRunnableAgain() throws Exception {
        Counting runnable = new Counting();
        runnable.run();
        assertEquals(1, runnable.entered.get());
        runnable.stop();
        assertTrue(runnable.isStopped());
        runnable.restart();
        assertFalse(runnable.isStopped());
        runnable.run();
        assertFalse(runnable.isStopped());
        assertEquals(2, runnable.entered.get());
    }

    @Test
    public void stoppedIsVisibleToAnotherThread() throws Exception {
        DeferredRunnable waiting = new DeferredRunnable() {
            @Override
            public Promise<Void, Void, Void> onShutDown() {
                return DeferredRunnable.ResolvedDeferredObject();
            }

            @Override
            public void runImpl() {
                // no sleep, no lock: only a volatile flag ends this loop
                while (!isStopped()) {
                    Thread.onSpinWait();
                }
            }

            @Override
            public String getRunnableName() {
                return "spinning";
            }
        };
        Thread thread = new Thread(waiting);
        thread.start();
        Thread.sleep(100);
        waiting.stop();
        thread.join(5000);
        assertFalse(thread.isAlive(), "the runnable did not see the stop");
    }
}

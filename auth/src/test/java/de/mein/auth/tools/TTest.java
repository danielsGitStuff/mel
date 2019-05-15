package de.mein.auth.tools;

import de.mein.Lok;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import org.junit.Test;
import org.junit.runners.model.TestTimedOutException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.*;

class TTest {

    class Dummy {
        Dummy(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return "Dummy: " + string;
        }

        String string;
    }

    private boolean triggerFlag;
    private Transaction t;
    private Transaction u;
    private Dummy A = new Dummy("AAA");
    private Dummy B = new Dummy("BBB");
    private Dummy C = new Dummy("CCC");
    private Thread thread1, thread2;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private WaitLock waitLock;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        t = null;
        T.reset();
        executor.shutdownNow();
        executor = Executors.newCachedThreadPool();
        waitLock = new WaitLock();
        triggerFlag = false;
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        Lok.debug("tear down");
        if (t != null)
            t.end();
    }

    @org.junit.jupiter.api.Test
    void lockIntersection() {
        t = T.lockingTransaction(A, B);
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            waitLock.unlock();
            executor.shutdownNow();
        });
        executor.submit(() -> {
            try {
                T.lockingTransaction(A, C);
                triggerFlag = true;
                fail("should not be reached");
                waitLock.unlock();
            } catch (Exception e) {
                return;
            }

        });
        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }

    @org.junit.jupiter.api.Test
    void lockWhole() {
        t = T.lockingTransaction(A, B);
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            waitLock.unlock();
            executor.shutdownNow();
        });
        executor.submit(() -> {
            try {
                T.lockingTransaction(A, B);
                triggerFlag = true;
                fail("should not be reached");
            } catch (Exception e) {
                e.printStackTrace();
            }

            waitLock.unlock();
        });
        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }

    @org.junit.jupiter.api.Test
    void lockDifferent() {
        t = T.lockingTransaction(A, B);

        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
            triggerFlag = true;
        });
        executor.submit(() -> {
            T.lockingTransaction(C);
            Lok.debug("unlocking");
            waitLock.unlock();
        });
        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }

    @org.junit.jupiter.api.Test
    void lockReadThenWrite() {
        t = T.lockingTransaction(T.read(A, B));
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            T.lockingTransaction(B);
            Lok.debug("unlocking");
            triggerFlag = true;
            waitLock.unlock();
        });
        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }

    @org.junit.jupiter.api.Test
    void lockWriteThenRead() {
        t = T.lockingTransaction(A, B);

        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            T.lockingTransaction(T.read(B));
            Lok.debug("unlocking");
            triggerFlag = true;
            waitLock.unlock();
        });
        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }

    @org.junit.jupiter.api.Test
    void lockLater() {
        t = T.tNoLock(A, B);
        u = T.lockingTransaction(A, B);
        executor.submit(() -> {
            // shutdown
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            t.run(() -> {
                // should not run
                Lok.error("should not run!");
                fail();
                triggerFlag = true;
            });
        });
        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }

    @org.junit.jupiter.api.Test
    void accessTwice() {
        t = T.tNoLock(A, B);
        executor.submit(() -> {
            N.oneLine(() -> Thread.sleep(100));
            Lok.debug("evaluating results...");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            t.run(() -> {
                Lok.debug("run1");
            });
            t.run(() -> {
                Lok.debug("run2");
                triggerFlag = true;
            });
        });

        Lok.debug("waiting");
        waitLock.lock().lock();
        assertTrue(triggerFlag);
    }
    @Test(timeout = 1000, expected = TestTimedOutException.class)
    public void lockAll() {

    }
}
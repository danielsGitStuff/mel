package de.mein.auth.tools;

import de.mein.Lok;
import de.mein.auth.tools.lock.Locker;
import de.mein.auth.tools.lock.Transaction;
import de.mein.auth.tools.lock.Read;
import org.junit.Test;
import org.junit.runners.model.TestTimedOutException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.*;

class LockerTest {

    private boolean triggerFlag;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        transaction = null;
        Locker.reset();
        executor.shutdownNow();
        executor = Executors.newCachedThreadPool();
        waitLock = new WaitLock();
        triggerFlag = false;
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        Lok.debug("tear down");
        if (transaction != null)
            transaction.end();
    }

    @org.junit.jupiter.api.Test
    void lockIntersection() {
        transaction = Locker.transaction(A, B);
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            waitLock.unlock();
            executor.shutdownNow();
        });
        executor.submit(() -> {
            try {
                Locker.transaction(A, C);
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
        transaction = Locker.transaction(A, B);
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            waitLock.unlock();
            executor.shutdownNow();
        });
        executor.submit(() -> {
            try {
                Locker.transaction(A, B);
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
        transaction = Locker.transaction(A, B);

        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
            triggerFlag = true;
        });
        executor.submit(() -> {
            Locker.transaction(C);
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
        transaction = Locker.transaction(new Read(A, B));
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            Locker.transaction(B);
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
        transaction = Locker.transaction(A, B);

        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            Locker.transaction(new Read(B));
            Lok.debug("unlocking");
            triggerFlag = true;
            waitLock.unlock();
        });
        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }


    private Transaction transaction;
    private String A = "AAA";
    private String B = "BBB";
    private String C = "CCC";

    private Thread thread1, thread2;

    private ExecutorService executor = Executors.newCachedThreadPool();
    private WaitLock waitLock;


    @Test(timeout = 1000, expected = TestTimedOutException.class)
    public void lockAll() {

    }
}
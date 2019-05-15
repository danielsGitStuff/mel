package de.mein.auth.tools;

import de.mein.Lok;
import de.mein.auth.tools.lock.KeyLocker;
import de.mein.auth.tools.lock.LockedTransaction;
import de.mein.auth.tools.lock.Read;
import org.junit.Test;
import org.junit.runners.model.TestTimedOutException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.*;

class KeyLockerTest {

    private boolean triggerFlag;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        transaction = null;
        KeyLocker.reset();
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
        transaction = KeyLocker.transaction(A, B);
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            waitLock.unlock();
            executor.shutdownNow();
        });
        executor.submit(() -> {
            try {
                KeyLocker.transaction(A, C);
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
        transaction = KeyLocker.transaction(A, B);
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            waitLock.unlock();
            executor.shutdownNow();
        });
        executor.submit(() -> {
            try {
                KeyLocker.transaction(A, B);
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
        transaction = KeyLocker.transaction(A, B);

        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
            triggerFlag = true;
        });
        executor.submit(() -> {
            KeyLocker.transaction(C);
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
        transaction = KeyLocker.transaction(new Read(A, B));
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            KeyLocker.transaction(B);
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
        transaction = KeyLocker.transaction(A, B);

        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            KeyLocker.transaction(new Read(B));
            Lok.debug("unlocking");
            triggerFlag = true;
            waitLock.unlock();
        });
        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }


    private LockedTransaction transaction;
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
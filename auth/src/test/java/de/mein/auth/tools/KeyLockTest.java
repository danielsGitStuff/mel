package de.mein.auth.tools;

import de.mein.Lok;
import de.mein.auth.service.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.model.TestTimedOutException;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KeyLockTest {
    private static Key key;
    private static String A = "AAA";
    private static String B = "BBB";
    private static String C = "CCC";

    private static Thread thread1, thread2;

    private static ExecutorService executor = Executors.newCachedThreadPool();
    private static WaitLock waitLock;

    @Before
    public void before() {
        key = null;
        executor.shutdown();
        executor = Executors.newCachedThreadPool();
        waitLock = new WaitLock();
    }

    @Test(timeout = 1000, expected = TestTimedOutException.class)
    public void lockAll() {
        key = KeyLocker.lockOn(A, B);
        executor.submit(() -> {
            Lok.debug("attempting to lock");
            KeyLocker.lockOn(A, C);
            waitLock.unlock();
        });
        waitLock.lock().lock();
        Lok.debug("done");
    }

    ;

}

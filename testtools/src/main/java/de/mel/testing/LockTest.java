package de.mel.testing;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public abstract class LockTest {

    protected Semaphore waitLock = new Semaphore(1);

    protected AtomicBoolean reach1, reach2, reach3, reach4;

    @Before
    public void before() {
        reach1 = new AtomicBoolean(false);
        reach2 = new AtomicBoolean(false);
        reach3 = new AtomicBoolean(false);
        reach4 = new AtomicBoolean(false);
        this.beforeImpl();
    }

    public abstract void beforeImpl();

    public void waitHere() {
        try {
            waitLock.acquire();
            waitLock.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void killAfter(long killAfterMillis, TestRunnable... runnables) {
        List<TestThread> threads = Arrays.stream(runnables).map(TestThread::new).collect(Collectors.toList());
        int count = 1;
        for (TestThread thread : threads) {
            thread.setName("T " + count);
            thread.start();
            count++;
        }
        AtomicBoolean oneThreadStillRunning = new AtomicBoolean(false);

        Thread killThread = new Thread(() -> {
            try {
                Thread.sleep(killAfterMillis);
                System.out.println("KILLING");
                for (TestThread thread : threads) {
                    if (!thread.isSuccessful()) {
                        thread.interrupt();
                        oneThreadStillRunning.set(true);
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                if (oneThreadStillRunning.get())
                    System.err.println("one thread did not finish in time");
                waitLock.release();
            }
        });
        if (killAfterMillis > 0) {
            killThread.start();
        }else {
            System.err.println("will not kill threads!");
        }
        waitHere();
    }

}

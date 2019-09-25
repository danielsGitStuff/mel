package de.mel.auth.tools;

import de.mel.Lok;
import de.mel.auth.tools.lock.T;
import de.mel.auth.tools.lock.Transaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.model.TestTimedOutException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.*;

public class TTest {

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

    @Before
    public void setUp() {
        t = null;
        T.reset();
        executor.shutdownNow();
        executor = Executors.newCachedThreadPool();
        waitLock = new WaitLock();
        triggerFlag = false;
    }

    @After
    public void tearDown() {
        Lok.debug("tear down");
        if (t != null)
            t.end();
    }

    @Test
    public void lockIntersection() {
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

    @Test
    public void lockWhole() {
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

    @Test
    public void lockDifferent() {
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

    @Test
    public void lockReadThenWrite() {
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

    @Test
    public void finallyTest() {
        Semaphore semaphore = new Semaphore(1, false);
//        t = T.lockingTransaction(A, B);
        Thread thread1 = new Thread(() -> {
            Transaction t1 = T.lockingTransaction(A, B);
            t1.run(() -> {
                Lok.debug("got t1");
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Lok.error("died sucessfully");
                    e.printStackTrace();
                } finally {
                    Lok.error("died finally");
                    semaphore.acquire();
                    Lok.error("got no semaphore");
                    t1.end();
                }
            });


        });

        Thread thread3 = new Thread(() -> {
//            N.r(() -> Thread.sleep(100));
            Lok.debug("trying to get t3");
            Transaction t3 = T.lockingTransaction(A, B);
            Lok.debug("got t3");
            t3.end();
            waitLock.unlock();
        });

        Thread thread2 = new Thread(() -> {
            N.r(() -> Thread.sleep(1000));
            Lok.debug("killing thread1");
            thread1.interrupt();
            thread3.start();
        });

        thread1.start();
        thread2.start();
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }


    @Test
    public void lockThenThreadDies() {
//        t = T.lockingTransaction(A, B);
        Thread thread1 = new Thread(() -> {
            Transaction t1 = T.lockingTransaction(A, B);
            Lok.debug("got t1");
            Semaphore semaphore = new Semaphore(1, false);
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Lok.error("died sucessfully");
                e.printStackTrace();
            } finally {
                Lok.error("died finally");
                t1.end();
            }
            N.r(() -> Thread.sleep(5000));

        });

        Thread thread3 = new Thread(() -> {
//            N.r(() -> Thread.sleep(100));
            Lok.debug("trying to get t3");
            Transaction t3 = T.lockingTransaction(A, B);
            Lok.debug("got t3");
            t3.end();
            waitLock.unlock();
        });

        Thread thread2 = new Thread(() -> {
            N.r(() -> Thread.sleep(1000));
            Lok.debug("killing thread1");
            thread1.interrupt();
            thread3.start();
        });

        thread1.start();
        thread2.start();
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }

    @Test
    public void lockWriteThenRead() {
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

    @Test
    public void lockLater() {
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

    @Test
    public void accessTwice() {
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

    @Test
    public void endWhileRun() {
        t = T.tNoLock(A, B);
        executor.submit(() -> {
            t.run(() -> {
                Lok.debug("sleep");
                Thread.sleep(100);
                Lok.debug("ending...");
                t.end();
                triggerFlag = true;
                waitLock.unlock();
            });
        });
        Lok.debug("waiting");
        waitLock.lock().lock();
        assertTrue(triggerFlag);
    }


//    @Test(timeout = 1000, expected = TestTimedOutException.class)
//    public void lockAll() {
//    t = T.lockingTransaction(A);
//    Transaction b = T.lockingTransaction(A);
//    Lok.debug(b);
//    }
}
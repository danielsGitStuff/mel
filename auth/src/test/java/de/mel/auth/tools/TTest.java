package de.mel.auth.tools;

import de.mel.Lok;
import de.mel.auth.tools.lock.Warden;

import de.mel.auth.tools.lock2.PrisonKey;
import de.mel.auth.tools.lock2.Prison;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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
    private Warden t;
    private Warden u;
    private Dummy A = new Dummy("AAA");
    private Dummy B = new Dummy("BBB");
    private Dummy C = new Dummy("CCC");
    private Thread thread1, thread2;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private WaitLock waitLock;

    @Before
    public void setUp() {
        t = null;
        de.mel.auth.tools.lock.P.reset();
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
        Lok.debug("confining A, C");
        t = de.mel.auth.tools.lock.P.confine(A, B);
        Lok.debug("confining A, C successful");
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            waitLock.unlock();
            executor.shutdownNow();
        });
        executor.submit(() -> {
            try {
                Lok.debug("confining C, A");
                de.mel.auth.tools.lock.P.confine(C, A);
                Lok.debug("confinging C, A sucessfull");
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
    public void lockIntersection2() {
        Lok.debug("confining A, C");
        t = de.mel.auth.tools.lock.P.confine(A, B);
        Lok.debug("confining A, C successful");
        Thread thread1 = new Thread(() -> {
            Lok.debug("confining B, A");
            u = de.mel.auth.tools.lock.P.confine(B, A);
            Lok.debug("confining B, A successful");
            waitLock.unlock();
            executor.shutdownNow();
        });
        Thread thread2 = new Thread(() -> {
            try {
                Lok.debug("confining C, A");
                de.mel.auth.tools.lock.P.confine(C, A);
                Lok.debug("confining C, A successful");
                triggerFlag = true;
                fail("should not be reached");
                waitLock.unlock();
            } catch (Exception e) {
                return;
            }
        });
        Thread thread3 = new Thread(() -> {
            N.r(() -> Thread.sleep(100));
            t.end();
        });
        thread1.start();
        thread2.start();
        thread3.start();

        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        assertFalse(triggerFlag);
        Lok.debug("done");
    }

    @Test
    public void lockIntersection3() {
        Lok.debug("confining A /0");
        PrisonKey t = Prison.confine(A).lock().end();
        Lok.debug("confining A /0 successful");
        AtomicInteger atomicInteger = new AtomicInteger(0);
        Thread thread1 = new Thread(() -> {
//            N.r(() -> Thread.sleep(100));
            Lok.debug("confining A /1");
            PrisonKey u = Prison.confine(A).lock();
            Lok.debug("confining A /1 successful");
            atomicInteger.addAndGet(1);
            N.r(() -> Thread.sleep(2000));
//            waitLock.unlock();

        });
        Thread thread2 = new Thread(() -> {
            try {
                N.r(() -> Thread.sleep(100));
                Lok.debug("confining A /2");
                PrisonKey x = Prison.confine(A).lock();
                Lok.debug("confining A /2 successful");
                atomicInteger.addAndGet(1);
                N.r(() -> Thread.sleep(2000));
//                triggerFlag = true;
//                fail("should not be reached");
                waitLock.unlock();
            } catch (Exception e) {
                return;
            }
        });
        Thread thread3 = new Thread(() -> {
            N.r(() -> Thread.sleep(300));
            Lok.debug("releasing A /0");
            t.end();
            Lok.debug("releasing A /0 successful");
        });
        thread1.start();
        thread2.start();
        thread3.start();
        new Thread(() -> N.r(() -> {
            Thread.sleep(500);
            Lok.debug("interrupting threads");
            thread1.interrupt();
            thread2.interrupt();
            Lok.debug("interrupting threads done");
        })).start();

        Lok.debug("wait 4 unlock");
        waitLock.lock().lock();
        Lok.debug("atomic count is " + atomicInteger.toString());
//        assertFalse(triggerFlag);
        assertEquals(1, atomicInteger.get());
        Lok.debug("done");
    }

    @Test
    public void lockWhole() {
        t = de.mel.auth.tools.lock.P.confine(A, B);
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            waitLock.unlock();
            executor.shutdownNow();
        });
        executor.submit(() -> {
            try {
                de.mel.auth.tools.lock.P.confine(A, B);
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
        t = de.mel.auth.tools.lock.P.confine(A, B);

        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
            triggerFlag = true;
        });
        executor.submit(() -> {
            de.mel.auth.tools.lock.P.confine(C);
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
        t = de.mel.auth.tools.lock.P.confine(de.mel.auth.tools.lock.P.read(A, B));
        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            de.mel.auth.tools.lock.P.confine(B);
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
            Warden t1 = de.mel.auth.tools.lock.P.confine(A, B);
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
            Warden t3 = de.mel.auth.tools.lock.P.confine(A, B);
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
            Warden t1 = de.mel.auth.tools.lock.P.confine(A, B);
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
            Warden t3 = de.mel.auth.tools.lock.P.confine(B, A);
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
        t = de.mel.auth.tools.lock.P.confine(A, B);

        executor.submit(() -> {
            N.r(() -> Thread.sleep(100));
            Lok.debug("sleep ends");
            executor.shutdownNow();
            waitLock.unlock();
        });
        executor.submit(() -> {
            de.mel.auth.tools.lock.P.confine(de.mel.auth.tools.lock.P.read(B));
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
        t = de.mel.auth.tools.lock.P.onProbation(A, B);
        u = de.mel.auth.tools.lock.P.confine(A, B);
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
        t = de.mel.auth.tools.lock.P.onProbation(A, B);
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
        t = de.mel.auth.tools.lock.P.onProbation(A, B);
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
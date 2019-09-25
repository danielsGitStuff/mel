package de.mel.thread;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.MelRunnable;
import de.mel.MelThread;
import de.mel.sql.RWLock;
import org.jdeferred.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Created by xor on 4/25/17.
 */
@SuppressWarnings("Duplicates")
public class ThreadTest {
    private Semaphore threadSemaphore = new Semaphore(1, true);
    private LinkedList<MelThread> threadQueue = new LinkedList<>();

    class TestThread extends MelThread {
        public TestThread(MelRunnable target) {
            super(target);
        }

        @Override
        public synchronized void start() {
            Lok.debug("TestThread.start");
            super.start();
        }


        @Override
        public void interrupt() {
            Lok.debug("TestThread.interrupt");
            super.interrupt();
        }

        @Override
        public boolean isInterrupted() {
            return super.isInterrupted();
        }

    }

    private ExecutorService executorService;

    @Before
    public void prepare() {
        this.executorService = Executors.newCachedThreadPool(r -> {
            MelThread melThread = null;
            try {
                threadSemaphore.acquire();
                melThread = threadQueue.poll();
                threadSemaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return melThread;
        });
    }

    @Test
    public void interrupt() {
        RWLock threadLock = new RWLock();
        RWLock endLock = new RWLock();
        threadLock.lockWrite();
        DeferredRunnable melRunnable1 = new DeferredRunnable() {

            @Override
            public String getRunnableName() {
                return "runnable1";
            }

            @Override
            public Promise<Void, Void, Void> onShutDown() {
                Lok.debug("ThreadTest.onShutDown1");
                return null;
            }

            @Override
            public void runImpl() {
                Lok.debug("ThreadTest.run1.lock");
                int i = 0;
                while (!isStopped())
                    i++;
                Lok.debug("ThreadTest.run1.stopped");
                threadLock.unlockWrite();
            }
        };
        DeferredRunnable melRunnable2 = new DeferredRunnable() {

            @Override
            public String getRunnableName() {
                return "runnable2";
            }

            @Override
            public Promise<Void, Void, Void> onShutDown() {
                Lok.debug("ThreadTest.onShutDown2");
                return null;
            }

            @Override
            public void runImpl() {
                Lok.debug("shutting down 1");
                melRunnable1.shutDown();
            }

        };
        execute(melRunnable1);
        execute(melRunnable2);
        threadLock.lockWrite();
    }

    public void execute(MelRunnable runnable) {
        try {
            threadSemaphore.acquire();
            threadQueue.add(new MelThread(runnable));
            threadSemaphore.release();
            executorService.execute(runnable);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @After
    public void after() {
        executorService.shutdownNow();
    }
}

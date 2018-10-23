package de.mein.thread;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.MeinThread;
import de.mein.sql.RWLock;
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
    private LinkedList<MeinThread> threadQueue = new LinkedList<>();

    class TestThread extends MeinThread {
        public TestThread(MeinRunnable target) {
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
            MeinThread meinThread = null;
            try {
                threadSemaphore.acquire();
                meinThread = threadQueue.poll();
                threadSemaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return meinThread;
        });
    }

    @Test
    public void interrupt() {
        RWLock threadLock = new RWLock();
        RWLock endLock = new RWLock();
        threadLock.lockWrite();
        DeferredRunnable meinRunnable1 = new DeferredRunnable() {

            @Override
            public String getRunnableName() {
                return "runnable1";
            }

            @Override
            public void onShutDown() {
                Lok.debug("ThreadTest.onShutDown1");
            }

            @Override
            public void runImpl() {
                Lok.debug("ThreadTest.run1.lock");
                int i = 0;
                while (!Thread.currentThread().isInterrupted())
                    i++;
                Lok.debug("ThreadTest.run1.stopped");
                threadLock.unlockWrite();
            }
        };
        DeferredRunnable meinRunnable2 = new DeferredRunnable() {

            @Override
            public String getRunnableName() {
                return "runnable2";
            }

            @Override
            public void onShutDown() {
                Lok.debug("ThreadTest.onShutDown2");
            }

            @Override
            public void runImpl() {
                meinRunnable1.shutDown();
            }

        };
        execute(meinRunnable1);
        execute(meinRunnable2);
        threadLock.lockWrite();
    }

    public void execute(MeinRunnable runnable) {
        try {
            threadSemaphore.acquire();
            threadQueue.add(new MeinThread(runnable));
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

package de.mel.auth.tools;

import de.mel.Lok;
import de.mel.MelRunnable;
import de.mel.MelThread;
import de.mel.auth.tools.lock.T;
import de.mel.auth.tools.lock.Transaction;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Created by xor on 5/18/17.
 */
public abstract class BackgroundExecutor {

    private static class RunnableWrapper implements Runnable {

        private final MelRunnable melRunnable;
        private Thread thread;

        RunnableWrapper(MelRunnable melRunnable) {
            this.melRunnable = melRunnable;
        }

        @Override
        public void run() {
            thread = Thread.currentThread();
            thread.setName(melRunnable.getRunnableName());
            melRunnable.run();
        }
    }

    private ExecutorService executorService;
    private final Semaphore threadSemaphore = new Semaphore(1, true);
    //private final LinkedList<MelThread> threadQueue = new LinkedList<>();
    private Object currentlyRunning;
    private final ThreadFactory threadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            if (r instanceof MelRunnable)
                return new MelThread((MelRunnable) r);
            return new Thread(r);
//            //noinspection Duplicates
//            try {
//                threadSemaphore.acquire();
//                melThread = threadQueue.poll();
//                threadSemaphore.release();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            //return melThread;
        }
    };

    public void execute(MelRunnable runnable) {
        // noinspection Duplicates
        T.lockingTransaction(this).run(() -> {
            threadSemaphore.acquire();
            runnable.onStart();
            if (executorService == null || executorService.isShutdown() || executorService.isTerminated())
                executorService = createExecutorService(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r);
                    }
                });

            threadSemaphore.release();
            RunnableWrapper wrapper = new RunnableWrapper(runnable);
            startedCounter++;
            executorService.execute(wrapper);
        }).end();
    }

    private int startedCounter = 0;

    public void shutDown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(2000, TimeUnit.MILLISECONDS);
    }

    /**
     * you should use threadFactory in "Executors.newCachedThreadPool()".
     * so naming works properly
     *
     * @param threadFactory
     * @return
     */
    protected abstract ExecutorService createExecutorService(ThreadFactory threadFactory);
}

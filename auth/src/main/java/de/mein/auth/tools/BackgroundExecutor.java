package de.mein.auth.tools;

import de.mein.MeinRunnable;
import de.mein.MeinThread;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Created by xor on 5/18/17.
 */
public abstract class BackgroundExecutor {
    private ExecutorService executorService;
    private final Semaphore threadSemaphore = new Semaphore(1, true);
    private final LinkedList<MeinThread> threadQueue = new LinkedList<>();
    private final ThreadFactory threadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            MeinThread meinThread = null;
            try {
                threadSemaphore.acquire();
                meinThread = threadQueue.poll();
                threadSemaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return meinThread;
        }
    };

    public void execute(MeinRunnable runnable) {
        try {
            if (executorService == null || (executorService != null && (executorService.isShutdown() || executorService.isTerminated())))
                executorService = createExecutorService(threadFactory);
            threadSemaphore.acquire();
            threadQueue.add(new MeinThread(runnable));
            threadSemaphore.release();
            executorService.execute(runnable);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

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

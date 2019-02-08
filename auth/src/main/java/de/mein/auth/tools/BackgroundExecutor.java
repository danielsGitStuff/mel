package de.mein.auth.tools;

import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.MeinThread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Created by xor on 5/18/17.
 */
public abstract class BackgroundExecutor {

    private static class RunnableWrapper implements Runnable {

        private final MeinRunnable meinRunnable;
        private Thread thread;

        RunnableWrapper(MeinRunnable meinRunnable) {
            this.meinRunnable = meinRunnable;
        }

        @Override
        public void run() {
            thread = Thread.currentThread();
            meinRunnable.run();
        }
    }

    private ExecutorService executorService;
    private final Semaphore threadSemaphore = new Semaphore(1, true);
    //private final LinkedList<MeinThread> threadQueue = new LinkedList<>();
    private Object currentlyRunning;
    private final ThreadFactory threadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            if (r instanceof MeinRunnable)
                return new MeinThread((MeinRunnable) r);
            return new Thread(r);
//            //noinspection Duplicates
//            try {
//                threadSemaphore.acquire();
//                meinThread = threadQueue.poll();
//                threadSemaphore.release();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            //return meinThread;
        }
    };

    public void execute(MeinRunnable runnable) {
        // noinspection Duplicates
        try {
            if (executorService == null || (executorService != null && (executorService.isShutdown() || executorService.isTerminated())))
                executorService = createExecutorService(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r);
                    }
                });
            //threadSemaphore.acquire();
            //if (threadQueue.size() > 1) {
            //  Lok.debug("BackgroundExecutor.execute");
            //}
            //threadQueue.add(new MeinThread(runnable));
            threadSemaphore.release();
            //todo debug
            RunnableWrapper wrapper = new RunnableWrapper(runnable);
            if (wrapper.meinRunnable.getRunnableName().toLowerCase().startsWith("meindriveclientservice for test"))
                Lok.debug("BackgroundExecutor.execute.debugkßc3ß4");
            startedCounter++;
            Lok.debug("starting thread no " + startedCounter);
            executorService.execute(wrapper);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

package de.mein.auth.service;

import de.mein.MeinRunnable;
import de.mein.MeinThread;

import java.io.File;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

/**
 * A Service comes with its own ExecutorService. You should execute all Runnables of your Service with this class.
 * It will stop all Threads/Runnables when shutDown() is called. This happens is a Service is shut down by @{@link MeinAuthService}
 * Created by xor on 5/2/16.
 */
public abstract class MeinService extends MeinWorker implements IMeinService {
    protected final File serviceInstanceWorkingDirectory;
    protected MeinAuthService meinAuthService;
    protected final String uuid;
    int bootLevel = 0;
    protected final Long serviceTypeId;
    private ExecutorService executorService;
    private final Semaphore threadSemaphore = new Semaphore(1, true);
    private final LinkedList<MeinThread> threadQueue = new LinkedList<>();
    //cache stuff
    protected final File cacheDirectory;
    private final ThreadFactory threadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            MeinThread meinThread = null;
            //noinspection Duplicates
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


    public MeinService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid) {
        this.meinAuthService = meinAuthService;
        this.serviceInstanceWorkingDirectory = serviceInstanceWorkingDirectory;
        this.serviceTypeId = serviceTypeId;
        this.uuid = uuid;
        executorService = createExecutorService(threadFactory);
        this.cacheDirectory = new File(serviceInstanceWorkingDirectory.getAbsolutePath() + File.separator + "cache");
        cacheDirectory.mkdirs();
    }

    public MeinAuthService getMeinAuthService() {
        return meinAuthService;
    }

    public Long getServiceTypeId() {
        return serviceTypeId;
    }

    public File getServiceInstanceWorkingDirectory() {
        return serviceInstanceWorkingDirectory;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + meinAuthService.getName();
    }

    public void execute(MeinRunnable runnable) {
        //noinspection Duplicates
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

    MeinService setBootLevel(int level) {
        this.bootLevel = level;
        return this;
    }

    public int getBootLevel() {
        return bootLevel;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName();
    }

    @Override
    public void onShutDown() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
        super.onShutDown();
    }

    /**
     * stop all workers threads here.
     */
    public void suspend() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
        super.suspend();
    }

    public void resume() {

    }

    /**
     * you should use threadFactory in "Executors.newCachedThreadPool()".
     * so naming works properly
     *
     * @param threadFactory
     * @return
     */
    protected abstract ExecutorService createExecutorService(ThreadFactory threadFactory);

    public File getCacheDirectory() {
        return cacheDirectory;
    }


}

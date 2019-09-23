package de.mein.auth.service;

import de.mein.core.serialize.SerializableEntity;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.MeinThread;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Service comes with its own ExecutorService. You should execute all Runnables of your Service with this class.
 * It will stop all Threads/Runnables when shutDown() is called. This happens is a Service is shut down by @{@link MeinAuthService}
 * Created by xor on 5/2/16.
 */
public abstract class MeinService extends MeinWorker implements IMeinService {
    protected final File serviceInstanceWorkingDirectory;
    protected final String uuid;
    //    private Map<String, MeinIsolatedProcess> isolatedProcessMap = new HashMap<>();
    protected final Long serviceTypeId;
    //cache stuff
    protected final File cacheDirectory;
    private final Bootloader.BootLevel bootLevel;
    private final Semaphore threadSemaphore = new Semaphore(1, true);
    private final LinkedList<MeinThread> threadQueue = new LinkedList<>();
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
    protected MeinAuthService meinAuthService;
    private ReentrantLock isolatedLock = new ReentrantLock();
    private Map<String, DeferredObject<? extends MeinIsolatedProcess, Exception, Void>> isolatedDeferredMap = new HashMap<>();
    private Bootloader.BootLevel reachedBootLevel;
    private ExecutorService executorService;

    public MeinService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, Bootloader.BootLevel bootLevel) {
        this.meinAuthService = meinAuthService;
        this.serviceInstanceWorkingDirectory = serviceInstanceWorkingDirectory;
        this.serviceTypeId = serviceTypeId;
        this.uuid = uuid;
        this.bootLevel = bootLevel;
        this.reachedBootLevel = Bootloader.BootLevel.NONE;
        executorService = createExecutorService(threadFactory);
        this.cacheDirectory = new File(serviceInstanceWorkingDirectory.getAbsolutePath() + File.separator + "cache");
        cacheDirectory.mkdirs();
    }

    @Override
    public synchronized void onIsolatedConnectionEstablished(MeinIsolatedProcess isolatedProcess) {
        final String key = isolatedProcess.getPartnerCertificateId() + "." + isolatedProcess.getPartnerServiceUuid() + "." + isolatedProcess.getClass().getSimpleName();
        isolatedLock.lock();
        DeferredObject<MeinIsolatedProcess, Exception, Void> deferredObject = new DeferredObject<>();
        deferredObject.resolve(isolatedProcess);
        isolatedDeferredMap.put(key, deferredObject);
        isolatedLock.unlock();
    }

    @Override
    public void onIsolatedConnectionClosed(MeinIsolatedProcess isolatedProcess) {
        Lok.debug("isolated connection closed");
        final String key = isolatedProcess.getPartnerCertificateId() + "." + isolatedProcess.getPartnerServiceUuid() + "." + isolatedProcess.getClass().getSimpleName();
        isolatedLock.lock();
        isolatedDeferredMap.remove(key);
        isolatedLock.unlock();
    }

    public synchronized <T extends MeinIsolatedProcess> Promise<T, Exception, Void> getIsolatedProcess(Class<T> processClass, Long partnerCertId, String partnerServiceUuid) throws InterruptedException, SqlQueriesException {
        final String key = partnerCertId + "." + partnerServiceUuid + "." + processClass.getSimpleName();
        isolatedLock.lock();
        DeferredObject<T, Exception, Void> alreadyDeferred = (DeferredObject<T, Exception, Void>) isolatedDeferredMap.get(key);
        if (alreadyDeferred != null && (alreadyDeferred.isResolved() || alreadyDeferred.isPending())) {
            isolatedLock.unlock();
            return alreadyDeferred;
        }
        DeferredObject<T, Exception, Void> deferred = meinAuthService.connectToService(processClass, partnerCertId, partnerServiceUuid, uuid, null, null, null);
        isolatedDeferredMap.put(key, deferred);
        deferred.done(result -> {
            Lok.debug("am i first?");
        });
        isolatedLock.unlock();
        return deferred;
    }

    public Bootloader.BootLevel getServiceBootType() {
        return bootLevel;
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


    public Bootloader.BootLevel getBootLevel() {
        return bootLevel;
    }

    public Bootloader.BootLevel getReachedBootLevel() {
        return reachedBootLevel;
    }

    public void setReachedBootLevel(Bootloader.BootLevel level) {
        this.reachedBootLevel = level;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName();
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
       return super.onShutDown();
    }

    /**
     * stop all workers threads here.
     */
    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
        super.stop();
    }

    public void resume() {
//        execute(this);
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


    /**
     * The other side is asking for services it might want to communicate with and you answer with a list of allowed
     * {@link de.mein.auth.data.db.ServiceJoinServiceType}s. Additional info (that is not part auf the MeinAuth databse) might be required.
     * Return it here.
     *
     * @return
     */
    public SerializableEntity addAdditionalServiceInfo() {
        return null;
    }
}

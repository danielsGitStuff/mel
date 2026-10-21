package de.mel.auth.service;

import de.mel.core.serialize.SerializableEntity;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import de.mel.Lok;
import de.mel.MelRunnable;
import de.mel.MelThread;
import de.mel.auth.data.IsolationDetails;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.sql.SqlQueriesException;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Service comes with its own ExecutorService. You should execute all Runnables of your Service with this class.
 * It will stop all Threads/Runnables when shutDown() is called. This happens is a Service is shut down by @{@link MelAuthServiceImpl}
 * Created by xor on 5/2/16.
 */
public abstract class MelService extends MelWorker implements IMelService {
    protected final File serviceInstanceWorkingDirectory;
    protected final String uuid;
    //    private Map<String, MelIsolatedProcess> isolatedProcessMap = new HashMap<>();
    protected final Long serviceTypeId;
    //cache stuff
    protected final File cacheDirectory;
    private final Bootloader.BootLevel bootLevel;
    private final Semaphore threadSemaphore = new Semaphore(1, true);
    private final LinkedList<MelThread> threadQueue = new LinkedList<>();
    private final ThreadFactory threadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            MelThread melThread = null;
            //noinspection Duplicates
            try {
                threadSemaphore.acquire();
                melThread = threadQueue.poll();
                threadSemaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return melThread;
        }
    };
    protected MelAuthService melAuthService;
    private ReentrantLock isolatedLock = new ReentrantLock();
    private Map<String, DeferredObject<? extends MelIsolatedProcess, Exception, Void>> isolatedDeferredMap = new HashMap<>();
    private Bootloader.BootLevel reachedBootLevel;
    private ExecutorService executorService;

    public MelService(MelAuthService melAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, Bootloader.BootLevel bootLevel) {
        this.melAuthService = melAuthService;
        this.serviceInstanceWorkingDirectory = serviceInstanceWorkingDirectory;
        this.serviceTypeId = serviceTypeId;
        this.uuid = uuid;
        this.bootLevel = bootLevel;
        this.reachedBootLevel = Bootloader.BootLevel.NONE;
        executorService = createExecutorService(threadFactory);
        this.cacheDirectory = new File(serviceInstanceWorkingDirectory.getAbsolutePath() + File.separator + "cache");
        cacheDirectory.mkdirs();
    }

    /**
     * the isolated process classes a partner may ask this service for, by name
     */
    private final Map<String, Class<? extends MelIsolatedProcess>> allowedIsolatedProcesses = new ConcurrentHashMap<>();

    /**
     * Partners may open isolated connections to this service that run this process. {@link MelIsolatedFileProcess} is always allowed.
     */
    protected void allowIsolatedProcess(Class<? extends MelIsolatedProcess> processClass) {
        allowedIsolatedProcesses.put(processClass.getCanonicalName(), processClass);
    }

    @Override
    public MelIsolatedProcess createIsolatedProcess(IsolationDetails details, MelAuthSocket melAuthSocket, Long partnerCertificateId) throws Exception {
        Class<? extends MelIsolatedProcess> processClass = allowedIsolatedProcesses.get(details.getProcessClass());
        if (processClass == null && MelIsolatedFileProcess.class.getCanonicalName().equals(details.getProcessClass()))
            processClass = MelIsolatedFileProcess.class;
        if (processClass == null)
            return null;
        return MelIsolatedProcess.instance(processClass, melAuthSocket, this, partnerCertificateId, details.getSourceService(), details.getIsolationUuid());
    }

    private static String isolatedKey(MelIsolatedProcess process) {
        return isolatedKey(process.getPartnerCertificateId(), process.getPartnerServiceUuid(), process.getClass());
    }

    private static String isolatedKey(Long partnerCertId, String partnerServiceUuid, Class<?> processClass) {
        return partnerCertId + "." + partnerServiceUuid + "." + processClass.getSimpleName();
    }

    /**
     * Isolated connections that are up, by their uuid. There may be several to the same partner service.
     */
    private final Map<String, MelIsolatedProcess> establishedIsolated = new HashMap<>();

    @Override
    public void onIsolatedConnectionEstablished(MelIsolatedProcess isolatedProcess) {
        isolatedLock.lock();
        try {
            establishedIsolated.put(isolatedProcess.getIsolatedUuid(), isolatedProcess);
        } finally {
            isolatedLock.unlock();
        }
    }

    @Override
    public void onIsolatedConnectionClosed(MelIsolatedProcess isolatedProcess) {
        Lok.debug("isolated connection closed");
        isolatedLock.lock();
        try {
            // only this one, others to the same partner service stay
            establishedIsolated.remove(isolatedProcess.getIsolatedUuid(), isolatedProcess);
        } finally {
            isolatedLock.unlock();
        }
    }

    /**
     * An open isolated connection to that partner service, if there is one: one that the partner opened or one that we opened. Otherwise one is opened.
     * Several isolated connections to the same partner service may exist, closing one does not affect the others.
     */
    @SuppressWarnings("unchecked")
    public synchronized <T extends MelIsolatedProcess> Promise<T, Exception, Void> getIsolatedProcess(Class<T> processClass, Long partnerCertId, String partnerServiceUuid) throws InterruptedException, SqlQueriesException {
        final String key = isolatedKey(partnerCertId, partnerServiceUuid, processClass);
        isolatedLock.lock();
        try {
            for (MelIsolatedProcess established : establishedIsolated.values()) {
                if (processClass.isInstance(established) && key.equals(isolatedKey(established)) && established.isOpen()) {
                    DeferredObject<T, Exception, Void> reuse = new DeferredObject<>();
                    reuse.resolve((T) established);
                    return reuse;
                }
            }
            DeferredObject<T, Exception, Void> alreadyDeferred = (DeferredObject<T, Exception, Void>) isolatedDeferredMap.get(key);
            if (alreadyDeferred != null && (alreadyDeferred.isPending())) {
                return alreadyDeferred;
            }
            DeferredObject<T, Exception, Void> deferred = melAuthService.connectToService(processClass, partnerCertId, partnerServiceUuid, uuid, null, null, null);
            isolatedDeferredMap.put(key, deferred);
            deferred.always((state, resolved, rejected) -> {
                // from here on the established isolated connections are looked at, a failed attempt must not be handed out again
                isolatedLock.lock();
                try {
                    isolatedDeferredMap.remove(key, deferred);
                } finally {
                    isolatedLock.unlock();
                }
            });
            return deferred;
        } finally {
            isolatedLock.unlock();
        }
    }

    public synchronized <T extends MelIsolatedProcess> Promise<T, Exception, Void> getIsolatedProcess2(Class<T> processClass, Long partnerCertId, String partnerServiceUuid) throws InterruptedException, SqlQueriesException {
        DeferredObject<T,Exception,Void> deferred = new DeferredObject<>();
        deferred.reject(new Exception("DEBUG exception. Isolated connection always fails!"));
        return deferred;
//        final String key = partnerCertId + "." + partnerServiceUuid + "." + processClass.getSimpleName();
//        isolatedLock.lock();
//        DeferredObject<T, Exception, Void> alreadyDeferred = (DeferredObject<T, Exception, Void>) isolatedDeferredMap.get(key);
//        if (alreadyDeferred != null && (alreadyDeferred.isResolved() || alreadyDeferred.isPending())) {
//            isolatedLock.unlock();
//            return alreadyDeferred;
//        }
//        DeferredObject<T, Exception, Void> deferred = melAuthService.connectToService(processClass, partnerCertId, partnerServiceUuid, uuid, null, null, null);
//        isolatedDeferredMap.put(key, deferred);
//        deferred.done(result -> {
//            Lok.debug("am i first?");
//        });
//        isolatedLock.unlock();
//        return deferred;
    }

    public Bootloader.BootLevel getServiceBootType() {
        return bootLevel;
    }

    public MelAuthService getMelAuthService() {
        return melAuthService;
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
        return getClass().getSimpleName() + "." + melAuthService.getName();
    }

    public void execute(MelRunnable runnable) {
        //noinspection Duplicates
        try {
            if (executorService == null || (executorService != null && (executorService.isShutdown() || executorService.isTerminated())))
                executorService = createExecutorService(threadFactory);
            threadSemaphore.acquire();
            threadQueue.add(new MelThread(runnable));
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
        return getClass().getSimpleName() + " for " + melAuthService.getName();
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
     * {@link de.mel.auth.data.db.ServiceJoinServiceType}s. Additional info (that is not part auf the MelAuth databse) might be required.
     * Return it here.
     *
     * @return
     */
    public SerializableEntity addAdditionalServiceInfo() {
        return null;
    }
}

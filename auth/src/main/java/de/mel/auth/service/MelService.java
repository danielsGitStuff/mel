package de.mel.auth.service;

import de.mel.core.serialize.SerializableEntity;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import de.mel.Lok;
import de.mel.MelRunnable;
import de.mel.MelThread;
import de.mel.auth.data.IsolationDetails;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.sql.SqlQueriesException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private Map<String, PromiseImpl<? extends MelIsolatedProcess, Exception>> isolatedDeferredMap = new HashMap<>();
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

    /**
     * who waits for an isolated connection to be registered here, by its uuid
     */
    private final Map<String, List<PromiseImpl<MelIsolatedProcess, Exception>>> waitingForIsolated = new HashMap<>();

    @Override
    public void onIsolatedConnectionEstablished(MelIsolatedProcess isolatedProcess) {
        List<PromiseImpl<MelIsolatedProcess, Exception>> waiting;
        isolatedLock.lock();
        try {
            establishedIsolated.put(isolatedProcess.getIsolatedUuid(), isolatedProcess);
            waiting = waitingForIsolated.remove(isolatedProcess.getIsolatedUuid());
        } finally {
            isolatedLock.unlock();
        }
        if (waiting != null)
            for (PromiseImpl<MelIsolatedProcess, Exception> promise : waiting)
                if (promise.isPending())
                    promise.resolveOk(isolatedProcess);
    }

    /**
     * The isolated connection with that uuid, as soon as it is registered here. The side that accepted a connection registers it after it answered,
     * so the partner may already use it (ask for files on it) a moment before.
     *
     * @param timeoutMs fails if there is none by then
     */
    public Promise<MelIsolatedProcess, Exception> whenIsolatedEstablished(String isolatedUuid, long timeoutMs) {
        PromiseImpl<MelIsolatedProcess, Exception> promise = PromiseImpl.withoutThrowingOnException();
        isolatedLock.lock();
        try {
            MelIsolatedProcess established = establishedIsolated.get(isolatedUuid);
            if (established != null && established.isOpen())
                return promise.resolveOk(established);
            waitingForIsolated.computeIfAbsent(isolatedUuid, k -> new ArrayList<>()).add(promise);
        } finally {
            isolatedLock.unlock();
        }
        try {
            melAuthService.getTimeoutScheduler().schedule(() -> {
                isolatedLock.lock();
                try {
                    List<PromiseImpl<MelIsolatedProcess, Exception>> list = waitingForIsolated.get(isolatedUuid);
                    if (list != null) {
                        list.remove(promise);
                        if (list.isEmpty())
                            waitingForIsolated.remove(isolatedUuid);
                    }
                } finally {
                    isolatedLock.unlock();
                }
                if (promise.isPending())
                    promise.resolveError(new java.io.IOException("no isolated connection " + isolatedUuid));
            }, timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            // shutting down
            if (promise.isPending())
                promise.resolveError(e);
        }
        return promise;
    }

    /**
     * @return the isolated connections of this service that are up right now, whoever opened them
     */
    public List<MelIsolatedProcess> getEstablishedIsolatedProcesses() {
        isolatedLock.lock();
        try {
            return new ArrayList<>(establishedIsolated.values());
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
    public synchronized <T extends MelIsolatedProcess> Promise<T, Exception> getIsolatedProcess(Class<T> processClass, Long partnerCertId, String partnerServiceUuid) throws InterruptedException, SqlQueriesException {
        final String key = isolatedKey(partnerCertId, partnerServiceUuid, processClass);
        isolatedLock.lock();
        try {
            for (MelIsolatedProcess established : establishedIsolated.values()) {
                if (processClass.isInstance(established) && key.equals(isolatedKey(established)) && established.isOpen()) {
                    PromiseImpl<T, Exception> reuse = PromiseImpl.withoutThrowingOnException();
                    reuse.resolveOk((T) established);
                    return reuse;
                }
            }
            PromiseImpl<T, Exception> alreadyDeferred = (PromiseImpl<T, Exception>) isolatedDeferredMap.get(key);
            if (alreadyDeferred != null && (alreadyDeferred.isPending())) {
                return alreadyDeferred;
            }
            PromiseImpl<T, Exception> deferred = melAuthService.connectToService(processClass, partnerCertId, partnerServiceUuid, uuid, null, null, null);
            isolatedDeferredMap.put(key, deferred);
            // from here on the established isolated connections are looked at, a failed attempt must not be handed out again
            Runnable forget = () -> {
                isolatedLock.lock();
                try {
                    isolatedDeferredMap.remove(key, deferred);
                } finally {
                    isolatedLock.unlock();
                }
            };
            deferred.onDone(result -> forget.run()).onError(reason -> forget.run());
            return deferred;
        } finally {
            isolatedLock.unlock();
        }
    }

    public synchronized <T extends MelIsolatedProcess> Promise<T, Exception> getIsolatedProcess2(Class<T> processClass, Long partnerCertId, String partnerServiceUuid) throws InterruptedException, SqlQueriesException {
        PromiseImpl<T, Exception> deferred = PromiseImpl.withoutThrowingOnException();
        deferred.resolveError(new Exception("DEBUG exception. Isolated connection always fails!"));
        return deferred;
//        final String key = partnerCertId + "." + partnerServiceUuid + "." + processClass.getSimpleName();
//        isolatedLock.lock();
//        PromiseImpl<T, Exception> alreadyDeferred = (PromiseImpl<T, Exception>) isolatedDeferredMap.get(key);
//        if (alreadyDeferred != null && (alreadyDeferred.hasResult() || alreadyDeferred.isPending())) {
//            isolatedLock.unlock();
//            return alreadyDeferred;
//        }
//        PromiseImpl<T, Exception> deferred = melAuthService.connectToService(processClass, partnerCertId, partnerServiceUuid, uuid, null, null, null);
//        isolatedDeferredMap.put(key, deferred);
//        deferred.onDone(result -> {
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
    public Promise<Void, Void> onShutDown() {
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

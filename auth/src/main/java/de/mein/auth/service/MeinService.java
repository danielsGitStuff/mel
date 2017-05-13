package de.mein.auth.service;

import de.mein.MeinRunnable;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Service comes with its own ExecutorService. You should execute all Runnables of your Service with this class.
 * It will stop all Threads/Runnables when shutDown() is called. This happens is a Service is shut down by @{@link MeinAuthService}
 * Created by xor on 5/2/16.
 */
public abstract class MeinService extends MeinWorker implements IMeinService {
    protected final File serviceInstanceWorkingDirectory;
    protected MeinAuthService meinAuthService;
    protected Integer id;
    protected String uuid;
    protected ExecutorService executorService;

    public MeinService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory) {
        this.meinAuthService = meinAuthService;
        this.executorService = Executors.newCachedThreadPool();
        this.serviceInstanceWorkingDirectory = serviceInstanceWorkingDirectory;
    }

    public File getServiceInstanceWorkingDirectory() {
        return serviceInstanceWorkingDirectory;
    }

    public Integer getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + meinAuthService.getName();
    }

    public void execute(MeinRunnable runnable) {
        // todo debug
//        meinAuthService.execute(runnable);
        executorService.execute(runnable);
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName();
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public void onShutDown() {
        executorService.shutdown();

    }
}

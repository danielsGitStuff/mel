package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.data.db.Service;
import org.jdeferred.Promise;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every Service running in MeinAuth has to start somewhere. This is here.
 * It is responsible for creating new Services or start/boot existing ones.
 * To boot a service it can go though 2 levels. Level 1 should initialize database connections and other brief stuff.
 * If you got a possibly long lasting workload do this on level 2.
 * If your Service does not require a second level you could just skip it (see bootLevel2Impl).
 * When you have gone though all necessary levels and your Service is ready to consume messages from the outside world
 * you can register it to the {@link MeinAuthService} via registerMeinService().
 */
public abstract class Bootloader<T extends MeinService> {
    protected T meinService;
    protected Long typeId;
    protected File bootLoaderDir;
    protected MeinAuthService meinAuthService;
    protected AtomicInteger bootLevel = new AtomicInteger(0);


    public Long getTypeId() {
        return typeId;
    }

    public Bootloader setTypeId(Long typeId) {
        this.typeId = typeId;
        return this;
    }

    public abstract String getName();

    public abstract String getDescription();

    public final Promise<T, BootException, Void> bootLevel1(MeinAuthService meinAuthService, Service serviceDescription) throws BootException {
        if (bootLevel.get() == 0) {
            bootLevel.incrementAndGet();
            Promise<T, BootException, Void> promise = bootLevel1Impl(meinAuthService, serviceDescription);
            if (promise != null)
                promise.done(service -> {
                    meinService = service;
                    service.onBootLevel1Finished();
                });
            return promise;
        } else {
            Lok.error("Bootloader in " + this.bootLoaderDir + " was told to boot to level 1. But its current level  was not 0. current level=" + bootLevel.get());
            return null;
        }
    }

    /**
     * do whatever is necessary to bring your service up as long as it doesn't take much time. (read config files, establish db connections...)
     *
     * @param meinAuthService
     * @param serviceDescription
     * @return
     * @throws BootException
     */
    public abstract Promise<T, BootException, Void> bootLevel1Impl(MeinAuthService meinAuthService, Service serviceDescription) throws BootException;

    public void setBootLoaderDir(File bootLoaderDir) {
        this.bootLoaderDir = bootLoaderDir;
    }

    @Override
    public String toString() {
        return getName();
    }

    public void setMeinAuthService(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }

    public final Promise<Void, BootException, Void> bootLevel2() throws BootException {
        if (bootLevel.get() == 1) {
            bootLevel.incrementAndGet();
            Promise<Void, BootException, Void> promise = bootLevel2Impl();
            if (promise != null)
                promise.done(nil -> meinService.onBootLevel2Finished());
            return promise;
        } else {
            Lok.error("Bootloader in " + this.bootLoaderDir + " was told to boot to level 2. But its current level was not 1. current level=" + bootLevel.get());
            return null;
        }
    }

    /**
     * do long lasting work here. This is only executed if {@link de.mein.auth.service.power.PowerManager}.isHeavyWorkAllowed() says so.
     * If not this step is postponed until the {@link de.mein.auth.service.power.PowerManager} changed its mind.
     *
     * @return
     * @throws BootException
     */
    public Promise<Void, BootException, Void> bootLevel2Impl() throws BootException {
        return null;
    }

    public static class BootException extends Exception {
        public final Bootloader bootloader;

        public BootException(Bootloader bootloader, Exception e) {
            super(e);
            this.bootloader = bootloader;
        }

        public BootException(Bootloader bootloader, String s) {
            super(new Exception(s));
            this.bootloader = bootloader;
        }

    }
}

package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.data.db.Service;

import de.mel.auth.data.db.ServiceJoinServiceType;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Every Service running in MelAuth has to start somewhere. This is here.
 * It is responsible for creating new Services or start/boot existing ones.
 * To boot a service it can go though 2 levels. Level 1 should initialize database connections and other brief stuff.
 * If you got a possibly long lasting workload do this on level 2.
 * If your Service does not require a second level you could just skip it (see bootLevel2Impl).
 * When you have gone though all necessary levels and your Service is ready to consume messages from the outside world
 * you can register it to the {@link MelAuthServiceImpl} via registerMelService().
 */
public abstract class Bootloader<T extends MelService> {
    protected T melService;
    protected Long typeId;
    protected File bootLoaderDir;
    protected MelAuthService melAuthService;
    protected AtomicReference<BootLevel> bootLevel = new AtomicReference<>(BootLevel.NONE);


    public Long getTypeId() {
        return typeId;
    }

    public Bootloader setTypeId(Long typeId) {
        this.typeId = typeId;
        return this;
    }

    public abstract String getName();

    public abstract String getDescription();

    public final T bootLevelShort(MelAuthService melAuthService, Service serviceDescription) throws BootException {
        if (bootLevel.compareAndSet(BootLevel.NONE, BootLevel.SHORT)) {
            melService = bootLevelShortImpl(melAuthService, serviceDescription);
            melService.setReachedBootLevel(BootLevel.SHORT);
            melService.onBootLevel1Finished();
            return melService;
        } else {
            Lok.error("Bootloader in " + this.bootLoaderDir + " was told to boot to level 1. But its current level was not 0. current level=" + bootLevel.get());
            return null;
        }
    }

    /**
     * do whatever is necessary to bring your service up as long as it doesn't take much time. (read config files, establish db connections...)
     *
     * @param melAuthService
     * @param serviceDescription
     * @return
     * @throws BootException
     */
    public abstract T bootLevelShortImpl(MelAuthService melAuthService, Service serviceDescription) throws BootException;

    public void setBootLoaderDir(File bootLoaderDir) {
        this.bootLoaderDir = bootLoaderDir;
    }

    @Override
    public String toString() {
        return getName();
    }

    public void setMelAuthService(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;

    }

    public final Promise<Void, BootException, Void> bootLevelLong() throws BootException {
        if (bootLevel.compareAndSet(BootLevel.SHORT, BootLevel.LONG)) {
            Promise<Void, BootException, Void> promise = bootLevelLongImpl();
            if (promise != null)
                promise.done(nil -> {
                    melService.setReachedBootLevel(BootLevel.LONG);
                    melService.onBootLevel2Finished();
                });
            return promise;
        } else {
            Lok.error("Bootloader in " + this.bootLoaderDir + " was told to boot to level 2. But its current level was not 1. current level=" + bootLevel.get());
            return new DeferredObject<>();
        }
    }

    /**
     * do long lasting work here. This is only executed if {@link de.mel.auth.service.power.PowerManager}.isHeavyWorkAllowed() says so.
     * If not this step is postponed until the {@link de.mel.auth.service.power.PowerManager} changed its mind.
     *
     * @return
     * @throws BootException
     */
    public Promise<Void, BootException, Void> bootLevelLongImpl() throws BootException {
        return null;
    }

    public abstract void cleanUpDeletedService(T melService, String uuid);

    /**
     * @param service
     * @return whether or not an instance of this bootloader's service is capable of dealing with the other one
     */
    public abstract boolean isCompatiblePartner(ServiceJoinServiceType service);

    public enum BootLevel {
//        NONE(0), SHORT(1), LONG(2);
//        private int value;

        //        BootLevel(int value) {
//            this.value = value;
//        }
        NONE, SHORT, LONG;

        public boolean greaterOrEqual(BootLevel other) {
            return this.ordinal() >= other.ordinal();
        }
    }
}

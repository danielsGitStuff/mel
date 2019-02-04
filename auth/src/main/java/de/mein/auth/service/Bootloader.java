package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.data.db.Service;
import org.jdeferred.Promise;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every Service running in MeinAuth has to start somewhere. This is here.
 * It is responsible for creating new Services or start/boot existing ones.
 */
public abstract class Bootloader<T extends MeinService> {
    protected T meinService;
    protected Long typeId;
    protected File bootLoaderDir;
    protected MeinAuthService meinAuthService;
    protected AtomicInteger bootLevel = new AtomicInteger(0);

    public Bootloader() {

    }

    public Long getTypeId() {
        return typeId;
    }

    public Bootloader setTypeId(Long typeId) {
        this.typeId = typeId;
        return this;
    }

    public abstract String getName();

    public abstract String getDescription();

    public final Promise<T, BootException, Void> bootStage1(MeinAuthService meinAuthService, Service serviceDescription) throws BootException {
        if (bootLevel.get() == 0) {
            bootLevel.incrementAndGet();
            Promise<T, BootException, Void> promise = bootStage1Impl(meinAuthService, serviceDescription);
            if (promise != null)
                promise.done(service -> {
                    meinService = service;
                    service.bootLevel(1);
                });
            return promise;
        } else {
            Lok.error("Bootloader in " + this.bootLoaderDir + " was told to boot to level 1. But its current level  was not 0. current level=" + bootLevel.get());
            return null;
        }
    }

    public abstract Promise<T, BootException, Void> bootStage1Impl(MeinAuthService meinAuthService, Service serviceDescription) throws BootException;

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

    public final Promise<Void, BootException, Void> bootStage2() throws BootException {
        if (bootLevel.get() == 1) {
            bootLevel.incrementAndGet();
            Promise<Void, BootException, Void> promise = bootStage2Impl();
            if (promise != null)
                promise.done(nil -> meinService.bootLevel(2));
            return promise;
        } else {
            Lok.error("Bootloader in " + this.bootLoaderDir + " was told to boot to level 2. But its current level was not 1. current level=" + bootLevel.get());
            return null;
        }
    }

    public Promise<Void, BootException, Void> bootStage2Impl() throws BootException {
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

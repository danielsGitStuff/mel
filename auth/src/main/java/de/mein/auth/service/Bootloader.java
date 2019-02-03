package de.mein.auth.service;

import de.mein.auth.data.db.Service;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Every Service running in MeinAuth has to start somewhere. This is here.
 * It is responsible for creating new Services or start/boot existing ones.
 */
public abstract class Bootloader {

    protected Long typeId;
    protected File bootLoaderDir;
    protected MeinAuthService meinAuthService;

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

    public abstract Promise<Void, BootException, Void> bootStage1(MeinAuthService meinAuthService, Service serviceDescription) throws BootException;

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

    public Promise<Void, BootException, Void> bootStage2() throws BootException{
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

package de.mein.auth.boot;

import de.mein.auth.data.db.Service;
import de.mein.auth.service.MeinAuthService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Every Service running in MeinAuth has to start somewhere. This is here.
 * It is responsible for creating new Services or start/boot existing ones.
 */
public abstract class BootLoader {

    protected Long typeId;
    protected File bootLoaderDir;
    protected MeinAuthService meinAuthService;

    public BootLoader(){

    }

    public Long getTypeId() {
        return typeId;
    }

    public BootLoader setTypeId(Long typeId) {
        this.typeId = typeId;
        return this;
    }

    public abstract String getName();
    public abstract String getDescription();

    public abstract void boot(MeinAuthService meinAuthService, List<Service> services) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException;

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
}

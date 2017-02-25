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
 * Created by xor on 16.08.2016.
 */
public abstract class BootLoader {

    protected Long typeId;
    protected File bootLoaderDir;

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

    public abstract void boot(MeinAuthService meinAuthService, List<Service> serviceIdWorkingDirMap) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException;

    public void setBootLoaderDir(File bootLoaderDir) {
        this.bootLoaderDir = bootLoaderDir;
    }

    @Override
    public String toString() {
        return getName();
    }
}

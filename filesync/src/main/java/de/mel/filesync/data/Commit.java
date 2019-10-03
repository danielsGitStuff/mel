package de.mel.filesync.data;

import de.mel.Lok;
import de.mel.auth.data.cached.CachedList;
import de.mel.auth.service.Bootloader;
import de.mel.filesync.sql.Stage;

import java.io.File;

/**
 * Created by xor on 1/14/17.
 */
public class Commit extends CachedList<Stage> {
    private String serviceUuid;
    private Long basedOnVersion;

    public Commit() {
        Lok.debug("Commit.Commit");
        this.level = Bootloader.BootLevel.LONG;
    }

    public Commit(File cacheDir, Long cacheId, int partSize, String serviceUuuid) {
        super(cacheDir, cacheId, partSize);
        this.level = Bootloader.BootLevel.LONG;
        this.serviceUuid = serviceUuuid;
    }

    public Commit setBasedOnVersion(Long basedOnVersion) {
        this.basedOnVersion = basedOnVersion;
        return this;
    }

    public Long getBasedOnVersion() {
        return basedOnVersion;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }
}

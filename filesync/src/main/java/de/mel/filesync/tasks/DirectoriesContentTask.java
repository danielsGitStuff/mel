package de.mel.filesync.tasks;

import de.mel.auth.data.ServicePayload;
import de.mel.auth.service.Bootloader;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.sql.FsDirectory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by xor on 12/12/16.
 */
public class DirectoriesContentTask extends ServicePayload {
    private long version;
    private List<FsDirectory> result = new ArrayList<>();
    private Set<Long> ids;

    public DirectoriesContentTask(){
        intent =  FileSyncStrings.INTENT_DIRECTORY_CONTENT;
        this.level = Bootloader.BootLevel.LONG;
    }

    public DirectoriesContentTask setVersion(long version) {
        // todo use and check version property. fail and react if error.
        this.version = version;
        return this;
    }

    public DirectoriesContentTask setIDs(Set<Long> ids) {
        this.ids = ids;
        return this;
    }

    public Set<Long> getIds() {
        return ids;
    }

    public long getVersion() {
        return version;
    }

    public DirectoriesContentTask setResult(List<FsDirectory> result) {
        this.result = result;
        return this;
    }

    public List<FsDirectory> getResult() {
        return result;
    }
}

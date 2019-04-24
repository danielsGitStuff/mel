package de.mein.drive.tasks;

import de.mein.auth.data.ServicePayload;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.sql.FsDirectory;

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
        intent =  DriveStrings.INTENT_DIRECTORY_CONTENT;
    }

    public DirectoriesContentTask setVersion(long version) {
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

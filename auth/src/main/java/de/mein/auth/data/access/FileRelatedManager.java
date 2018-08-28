package de.mein.auth.data.access;

import de.mein.auth.file.AFile;
import de.mein.sql.RWLock;

import java.io.File;

/**
 * Created by xor on 4/26/16.
 */
public abstract class FileRelatedManager extends RWLock{
    protected AFile workingDirectory;
    protected boolean hadToInitialize = false;

    public FileRelatedManager(AFile workingDirectory) {
        this.workingDirectory = workingDirectory;
        if (!workingDirectory.exists()) {
            workingDirectory.mkdirs();
        }
    }

    public String createWorkingPath() {
        return workingDirectory.getAbsolutePath() + File.separator;
    }
    public boolean hadToInitialize() {
        return hadToInitialize;
    }

}

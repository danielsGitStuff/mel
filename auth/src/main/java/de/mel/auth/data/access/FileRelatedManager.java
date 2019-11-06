package de.mel.auth.data.access;

import de.mel.sql.RWLock;

import java.io.File;

/**
 * Created by xor on 4/26/16.
 */
public abstract class FileRelatedManager extends RWLock{
    protected File workingDirectory;
    protected boolean hadToInitialize = false;

    public FileRelatedManager(File workingDirectory) {
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

package de.mel.filesync.index.watchdog;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.BashToolsException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The only way to let Linux find to retrieve all files and folders modified after a certain time I figured out
 * is to start it with a reference folder. It then takes its timestamp and works properly. I did not figure out how to use
 * a "--modified-after $someNumber"-method. There is one but I only could get it to work with minutes instead of Unix timestamps.
 * Created by xor on 5/7/17.
 */
public class UnixReferenceFileHandler {
    private final AbstractFile directoryToQuery;
    private final AbstractFile pruneDir;
    private final File workingDirectory;
    private boolean refOnFile1 = true;
    private final File timeReferenceFile1;
    private final File timeReferenceFile2;

    public UnixReferenceFileHandler(File workingDirectory, AbstractFile directoryToQuery, AbstractFile pruneDir) {
        this.directoryToQuery = directoryToQuery;
        this.pruneDir = pruneDir;
        this.workingDirectory = workingDirectory;
        timeReferenceFile1 = new File(workingDirectory ,"time1");
        timeReferenceFile2 = new File(workingDirectory, "time2");
    }

    public void onStart() {
        createFile(timeReferenceFile1);
        createFile(timeReferenceFile2);
    }

    private void createFile(File timeReferenceFile) {
        if (timeReferenceFile.exists())
            timeReferenceFile.delete();
        timeReferenceFile.mkdirs();
    }

    public synchronized List<IFile> stuffModifiedAfter() throws IOException, BashToolsException {
        // take the older one as reference. but to avoid data loss, we recreate the other file before.
        // so no stuff which happened while the BashTools work gets lost.
        File refFile = (refOnFile1) ? timeReferenceFile2 : timeReferenceFile1;
        File otherFile = (refOnFile1) ? timeReferenceFile1 : timeReferenceFile2;
        refOnFile1 = !refOnFile1;
        otherFile.delete();
        otherFile.mkdirs();
        return BashTools.Companion.stuffModifiedAfter(AbstractFile.instance(refFile.getAbsolutePath()), directoryToQuery, pruneDir);
    }
}

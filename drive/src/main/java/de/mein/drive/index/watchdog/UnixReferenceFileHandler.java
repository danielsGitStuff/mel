package de.mein.drive.index.watchdog;

import de.mein.auth.file.AFile;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.BashToolsException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by xor on 5/7/17.
 */
public class UnixReferenceFileHandler {
    private final AFile directoryToQuery;
    private final AFile pruneDir;
    private final File workingDirectory;
    private boolean refOnFile1 = true;
    private final File timeReferenceFile1;
    private final File timeReferenceFile2;

    public UnixReferenceFileHandler(File workingDirectory, AFile directoryToQuery, AFile pruneDir) {
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

    public synchronized List<AFile> stuffModifiedAfter() throws IOException, BashToolsException {
        // take the older one as reference. but to avoid data loss, we recreate the other file before.
        // so no stuff which happened while the BashTools work gets lost.
        File refFile = (refOnFile1) ? timeReferenceFile2 : timeReferenceFile1;
        File otherFile = (refOnFile1) ? timeReferenceFile1 : timeReferenceFile2;
        refOnFile1 = !refOnFile1;
        otherFile.delete();
        otherFile.mkdirs();
        //todo debug
        if (refFile.lastModified()>otherFile.lastModified())
            System.err.println("dewjwojo94ig");
        return BashTools.stuffModifiedAfter(AFile.instance(refFile.getAbsolutePath()), directoryToQuery, pruneDir);
    }
}

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
    private final AFile workingDirectory;
    private boolean refOnFile1 = true;
    private final AFile timeReferenceFile1;
    private final AFile timeReferenceFile2;

    public UnixReferenceFileHandler(AFile workingDirectory, AFile directoryToQuery, AFile pruneDir) {
        this.directoryToQuery = directoryToQuery;
        this.pruneDir = pruneDir;
        this.workingDirectory = workingDirectory;
        timeReferenceFile1 = AFile.instance(workingDirectory ,"time1");
        timeReferenceFile2 = AFile.instance(workingDirectory, "time2");
    }

    public void onStart() {
        createFile(timeReferenceFile1);
        createFile(timeReferenceFile2);
    }

    private void createFile(AFile timeReferenceFile) {
        if (timeReferenceFile.exists())
            timeReferenceFile.delete();
        timeReferenceFile.mkdirs();
    }

    public synchronized List<String> stuffModifiedAfter() throws IOException, BashToolsException {
        // take the older one as reference. but to avoid data loss, we recreate the other file before.
        // so no stuff which happened while the BashTools work gets lost.
        AFile refFile = (refOnFile1) ? timeReferenceFile2 : timeReferenceFile1;
        AFile otherFile = (refOnFile1) ? timeReferenceFile1 : timeReferenceFile2;
        refOnFile1 = !refOnFile1;
        otherFile.delete();
        otherFile.mkdirs();
        //todo debug
        if (refFile.lastModified()>otherFile.lastModified())
            System.err.println("dewjwojo94ig");
        return BashTools.stuffModifiedAfter(refFile, directoryToQuery, pruneDir);
    }
}

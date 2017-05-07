package de.mein.drive.watchdog;

import de.mein.drive.index.BashTools;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by xor on 5/7/17.
 */
public class UnixReferenceFileHandler {
    private final File directory;
    private final File pruneDir;
    private boolean refOnFile1 = true;
    private File timeReferenceFile1 = new File("time1");
    private File timeReferenceFile2 = new File("time2");

    public UnixReferenceFileHandler(File directory, File pruneDir) {
        this.directory = directory;
        this.pruneDir = pruneDir;
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

    public synchronized List<String> stuffModifiedAfter() throws IOException {
        // take the older one as reference. but to avoid data loss, we recreate the other file before.
        // so no stuff which happened while the BashTools work gets lost.
        File refFile = (refOnFile1) ? timeReferenceFile1 : timeReferenceFile2;
        File otherFile = (refOnFile1) ? timeReferenceFile2 : timeReferenceFile1;
        refOnFile1 = !refOnFile1;
        otherFile.delete();
        otherFile.mkdirs();
        return BashTools.stuffModifiedAfter(refFile, directory, pruneDir);
    }
}

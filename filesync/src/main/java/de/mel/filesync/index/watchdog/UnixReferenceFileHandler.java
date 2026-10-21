package de.mel.filesync.index.watchdog;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.filesync.bash.AutoKlausIterator;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.BashToolsException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds what changed since the last time we looked, with `find`: events get lost on every platform, so this is the safety net (and much faster than walking the files).
 * <p>
 * If `find` can take a time ({@link BashTools.Companion#canFindByTime()}) that is all there is: the start of the last scan, minus a margin for file systems that
 * round times, so a change is never missed because it has the same time as the scan before. What is found twice is no problem, the indexer sees that it did not change.
 * <p>
 * Where it cannot (some versions of Android's find) a reference file is used: `find -newercc` takes its time from a file. Two files are used in turns:
 * the older one is the reference, the other one is recreated first, so nothing that happens while `find` runs gets lost.
 * Created by xor on 5/7/17.
 */
public class UnixReferenceFileHandler {
    /**
     * more than any file system rounds times (FAT: two seconds)
     */
    static final long MARGIN_MS = 2000;
    private final IFile directoryToQuery;
    private final IFile pruneDir;
    private final File workingDirectory;
    private boolean refOnFile1 = true;
    private final File timeReferenceFile1;
    private final File timeReferenceFile2;
    private long lastScanStart;

    public UnixReferenceFileHandler(File workingDirectory, IFile directoryToQuery, IFile pruneDir) {
        this.directoryToQuery = directoryToQuery;
        this.pruneDir = pruneDir;
        this.workingDirectory = workingDirectory;
        timeReferenceFile1 = new File(workingDirectory ,"time1");
        timeReferenceFile2 = new File(workingDirectory, "time2");
    }

    public void onStart() {
        lastScanStart = System.currentTimeMillis();
        if (BashTools.Companion.canFindByTime())
            return;
        createFile(timeReferenceFile1);
        createFile(timeReferenceFile2);
    }

    private void createFile(File timeReferenceFile) {
        if (timeReferenceFile.exists())
            timeReferenceFile.delete();
        timeReferenceFile.mkdirs();
    }

    public synchronized List<IFile> stuffModifiedAfter() throws IOException, BashToolsException {
        if (BashTools.Companion.canFindByTime()) {
            // taken before find starts: what changes while it runs is found next time
            long since = lastScanStart - MARGIN_MS;
            lastScanStart = System.currentTimeMillis();
            List<IFile> result = new ArrayList<>();
            try (AutoKlausIterator<IFile> found = BashTools.Companion.stuffModifiedAfter(directoryToQuery, pruneDir, since)) {
                while (found.hasNext())
                    result.add(found.next());
            } catch (IOException | RuntimeException e) {
                // nothing was looked at: try the same time again
                lastScanStart = since + MARGIN_MS;
                throw e;
            } catch (Exception e) {
                lastScanStart = since + MARGIN_MS;
                throw new IOException(e);
            }
            return result;
        }
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

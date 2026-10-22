package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.TimeUnit;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.auth.tools.lock2.P;
import de.mel.filesync.index.watchdog.FileWatcher;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.FsFile;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.dao.FsDao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A commit of a stage set works on a copy of the fs table (fswrite) and puts it in place of the fs table at its end, all under the lock of the fs table.
 * What the file distribution writes meanwhile must wait for that lock, else the end of the commit undoes it: a file in its place that the table calls not synced,
 * or an entry that was dropped and is back (seen with the dump, where a second name for stored content is a reference or left out).
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class DistributionDuringCommitTest {
    @TempDir
    File dir;

    @Test
    public void aCopyPlacedWhileAStageSetIsCommittedIsSyncedAfterwards() throws Exception {
        File serverRoot = new File(dir, "serverRoot");
        FileSyncInstances.writeFile(serverRoot, "a/one.bin", 50_000, 5);
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24590)) {
            // the watcher must not index the copy: that would set the entry right by other means
            FileWatcher.quietRepetitions = 1000;
            instances.createServer();
            FileSyncDatabaseManager database = instances.serverService.getFileSyncDatabaseManager();
            FsDao fsDao = database.getFsDao();
            assertTrue(TwoInstances.waitFor(() -> {
                try {
                    GenericFSEntry one = fsDao.getGenericByPathAndName("/a/", "one.bin");
                    return one != null && Boolean.TRUE.equals(one.getSynced().v());
                } catch (Exception e) {
                    return false;
                }
            }, TwoInstances.TIMEOUT_SECONDS), "the server did not index its file");
            FsFile one = fsDao.getFile(fsDao.getGenericByPathAndName("/a/", "one.bin").getId().v());
            String hash = one.getContentHash().v();

            // a second place for the same content, as a commit from elsewhere makes it
            FsFile two = new FsFile();
            two.getName().v("two.bin");
            two.getContentHash().v(hash);
            two.getParentId().v(one.getParentId().v());
            two.getPath().v("/a/");
            two.getDepth().v(one.getDepth().v());
            two.getVersion().v(one.getVersion().v());
            two.getSize().v(one.getSize().v());
            two.getSynced().v(false);
            P.confine(fsDao).run(() -> fsDao.insert(two)).end();

            // a commit is going on (commitStage does the same with the lock held)
            BunchOfLocks commit = P.confine(fsDao);
            File twoOnDisk = new File(serverRoot, "a/two.bin");
            try {
                commit.run(() -> {
                    database.getFsWriteDao().prepare();
                    File oneOnDisk = new File(serverRoot, "a/one.bin");
                    assertTrue(instances.serverService.getSyncHandler().onFileTransferred(AbstractFile.instance(oneOnDisk), hash, commit, one), "nothing to distribute");
                    assertTrue(TwoInstances.waitFor(twoOnDisk::exists, TwoInstances.TIMEOUT_SECONDS), "the copy was not made");
                    // time for the distribution to write the fs table, if it does not wait
                    Thread.sleep(500);
                    database.getFsWriteDao().commit();
                });
            } finally {
                commit.end();
            }

            assertTrue(TwoInstances.waitFor(() -> {
                try {
                    FsFile entry = fsDao.getFile(two.getId().v());
                    return entry != null && Boolean.TRUE.equals(entry.getSynced().v());
                } catch (Exception e) {
                    return false;
                }
            }, TwoInstances.TIMEOUT_SECONDS), "the copy is in place, the fs table says it is not");
            assertNotNull(fsDao.getFile(two.getId().v()).getiNode().v(), "the entry does not know the file on the disk");
        }
    }
}

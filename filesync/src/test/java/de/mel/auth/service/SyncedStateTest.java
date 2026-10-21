package de.mel.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.mel.auth.file.AbstractFile;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.StageSet;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * After the first sync the client must know the files it just wrote exactly as they are on its disk: size, content hash, inode and modification time in the fs table.
 * That is how the indexer tells a file that changed from one that did not. If the sync leaves the fs table in a different state than the disk, the next index
 * sees the sync's own writes as changes of the user, commits them with an old version and starts a merge nobody asked for.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class SyncedStateTest {
    @TempDir
    File dir;

    private static final String[][] FILES = {
            {"", "first.bin"}, {"", "second one.bin"}, {"sub/", "in a dir.bin"}, {"sub/deeper/", "deeper.bin"}
    };

    private void prepare(File serverRoot) throws Exception {
        long seed = 1;
        for (String[] file : FILES)
            FileSyncInstances.writeFile(serverRoot, file[0] + file[1], 5_000 + seed * 1_000, seed++);
    }

    private static boolean synced(FileSyncInstances instances) {
        try {
            return FileSyncInstances.hashesBelow(instances.serverRoot).equals(FileSyncInstances.hashesBelow(instances.clientRoot));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * the client's database holds each file with what is on its disk
     */
    private static List<String> differences(File root, FileSyncDatabaseManager database) throws Exception {
        List<String> differences = new ArrayList<>();
        for (String[] f : FILES) {
            String path = "/" + f[0];
            File file = new File(root, f[0] + f[1]);
            GenericFSEntry entry = database.getFsDao().getGenericByPathAndName(path, f[1]);
            if (entry == null) {
                differences.add(path + f[1] + ": the fs table does not know it");
                continue;
            }
            FsBashDetails disk = BashTools.Companion.getFsBashDetails(AbstractFile.instance(file));
            if (!java.util.Objects.equals(entry.getSize().v(), file.length()))
                differences.add(path + f[1] + ": size " + entry.getSize().v() + " but the file has " + file.length());
            if (!FileSyncInstances.md5Of(file).equals(entry.getContentHash().v()))
                differences.add(path + f[1] + ": hash " + entry.getContentHash().v() + " but the file has " + FileSyncInstances.md5Of(file));
            if (!java.util.Objects.equals(entry.getiNode().v(), disk.getiNode()))
                differences.add(path + f[1] + ": inode " + entry.getiNode().v() + " but the file has " + disk.getiNode());
            // the creation time is not sent: the receiver's table has the one of its own disk (linux cannot set it)
            if (!java.util.Objects.equals(entry.getCreated().v(), disk.getCreated()))
                differences.add(path + f[1] + ": created " + entry.getCreated().v() + " but the file has " + disk.getCreated());
            if (!java.util.Objects.equals(entry.getModified().v(), disk.getModified()))
                differences.add(path + f[1] + ": modified " + entry.getModified().v() + " but the file has " + disk.getModified());
        }
        return differences;
    }

    @Test
    public void afterTheFirstSyncTheClientsFsTableAgreesWithItsDisk() throws Exception {
        prepare(new File(dir, "serverRoot"));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24500)) {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> synced(instances), 60), "the client did not get the files");
            FileSyncDatabaseManager database = instances.clientService.getFileSyncDatabaseManager();
            // the transfer is done when the files are there, the bookkeeping of it a bit later
            List<String> differences = new ArrayList<>();
            boolean agrees = TwoInstances.waitFor(() -> {
                try {
                    differences.clear();
                    differences.addAll(differences(instances.clientRoot, database));
                    return differences.isEmpty();
                } catch (Exception e) {
                    differences.add(e.toString());
                    return false;
                }
            }, 30);
            assertTrue(agrees, "the fs table of the client differs from its disk: " + differences);
        }
    }

    @Test
    public void afterTheFirstSyncTheServersFsTableAgreesWithItsDisk() throws Exception {
        prepare(new File(dir, "serverRoot"));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24510)) {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> synced(instances), 60), "the client did not get the files");
            FileSyncDatabaseManager database = instances.serverService.getFileSyncDatabaseManager();
            List<String> differences = differences(instances.serverRoot, database);
            assertTrue(differences.isEmpty(), "the fs table of the server differs from its disk: " + differences);
        }
    }

    /**
     * The indexer reads a file again only if it does not look the same as in the fs table (modified time, inode, size). A file the client received must look the same,
     * else every index of the client reads every file it ever received.
     */
    @Test
    public void aFileTheClientReceivedLooksTheSameAsInItsFsTableSoNobodyReadsItAgain() throws Exception {
        prepare(new File(dir, "serverRoot"));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24530)) {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> synced(instances), 60), "the client did not get the files");
            FileSyncDatabaseManager database = instances.clientService.getFileSyncDatabaseManager();
            List<String> looksChanged = new ArrayList<>();
            boolean same = TwoInstances.waitFor(() -> {
                try {
                    looksChanged.clear();
                    for (String[] f : FILES) {
                        File file = new File(instances.clientRoot, f[0] + f[1]);
                        GenericFSEntry entry = database.getFsDao().getGenericByPathAndName("/" + f[0], f[1]);
                        FsBashDetails disk = BashTools.Companion.getFsBashDetails(AbstractFile.instance(file));
                        if (entry == null || !de.mel.filesync.index.AbstractIndexer.sameAsInFs(entry, disk, file.length()))
                            looksChanged.add("/" + f[0] + f[1]);
                    }
                    return looksChanged.isEmpty();
                } catch (Exception e) {
                    looksChanged.add(e.toString());
                    return false;
                }
            }, 30);
            assertTrue(same, "the indexer would read these again, they look changed: " + looksChanged);
        }
    }

    /**
     * As with rsync -t: a file keeps the modification time of the sender, it does not get the time it was downloaded.
     * Best effort on platforms that cannot set it, but linux can.
     */
    @Test
    public void aReceivedFileKeepsTheModificationTimeOfTheSender() throws Exception {
        File serverRoot = new File(dir, "serverRoot");
        prepare(serverRoot);
        long old = 1_577_869_200_000L; // 2020-01-01
        for (String[] f : FILES)
            assertTrue(new File(serverRoot, f[0] + f[1]).setLastModified(old));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24540)) {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> synced(instances), 60), "the client did not get the files");
            FileSyncDatabaseManager database = instances.clientService.getFileSyncDatabaseManager();
            List<String> wrong = new ArrayList<>();
            boolean ok = TwoInstances.waitFor(() -> {
                wrong.clear();
                for (String[] f : FILES) {
                    long onDisk = new File(instances.clientRoot, f[0] + f[1]).lastModified();
                    if (onDisk != old)
                        wrong.add("/" + f[0] + f[1] + " on the disk of the client: " + onDisk);
                    try {
                        GenericFSEntry entry = database.getFsDao().getGenericByPathAndName("/" + f[0], f[1]);
                        if (entry == null || !java.util.Objects.equals(entry.getModified().v(), old))
                            wrong.add("/" + f[0] + f[1] + " in the fs table of the client: " + (entry == null ? null : entry.getModified().v()));
                    } catch (Exception e) {
                        wrong.add(e.toString());
                    }
                }
                return wrong.isEmpty();
            }, 30);
            assertTrue(ok, "the client's files should have the time of the server's (" + old + "): " + wrong);
        }
    }

    @Test
    public void aFileTheServerReceivedFromTheClientKeepsItsModificationTimeToo() throws Exception {
        prepare(new File(dir, "serverRoot"));
        long old = 1_577_869_200_000L; // 2020-01-01
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24550)) {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> synced(instances), 60), "the client did not get the files");
            instances.restartClient(() -> {
                try {
                    FileSyncInstances.writeFile(instances.clientRoot, "from the client.bin", 9_000, 42);
                    assertTrue(new File(instances.clientRoot, "from the client.bin").setLastModified(old));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            File onServer = new File(instances.serverRoot, "from the client.bin");
            assertTrue(TwoInstances.waitFor(() -> synced(instances), 60), "the server did not get the file");
            assertTrue(TwoInstances.waitFor(() -> onServer.lastModified() == old, 20), "the file has the time " + onServer.lastModified() + " on the server instead of " + old);
        }
    }

    /**
     * one download goes to several places: every one of them has its own time, a copy of it is no reason to forget that
     */
    @Test
    public void sameContentInTwoPlacesGetsEachItsOwnModificationTime() throws Exception {
        File serverRoot = new File(dir, "serverRoot");
        FileSyncInstances.writeFile(serverRoot, "a/one.bin", 400_000, 77);
        FileSyncInstances.writeFile(serverRoot, "b/two.bin", 400_000, 77);
        long first = 1_577_869_200_000L;
        long second = 1_483_228_800_000L;
        assertTrue(new File(serverRoot, "a/one.bin").setLastModified(first));
        assertTrue(new File(serverRoot, "b/two.bin").setLastModified(second));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24560)) {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> synced(instances), 60), "the client did not get the files");
            File one = new File(instances.clientRoot, "a/one.bin");
            File two = new File(instances.clientRoot, "b/two.bin");
            assertTrue(TwoInstances.waitFor(() -> one.lastModified() == first && two.lastModified() == second, 30),
                    "one " + one.lastModified() + " (should be " + first + "), two " + two.lastModified() + " (should be " + second + ")");
        }
    }

    @Test
    public void aClientThatJustSyncedHasNothingToCommitAndStaysQuiet() throws Exception {
        prepare(new File(dir, "serverRoot"));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24520)) {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> synced(instances), 60), "the client did not get the files");
            FileSyncDatabaseManager database = instances.clientService.getFileSyncDatabaseManager();
            long serverVersion = instances.serverService.getFileSyncDatabaseManager().getLatestVersion();
            // time for the watcher of the client to see the writes of the sync and to do whatever it does with them
            Thread.sleep(15_000);
            StageSet leftover = null;
            for (long id = 1; id < 30 && leftover == null; id++) {
                StageSet stageSet = database.getStageDao().getStageSetById(id);
                if (stageSet != null && de.mel.filesync.data.FileSyncStrings.STAGESET_SOURCE_FS.equals(stageSet.getSource().v()) && database.getStageDao().stageSetHasContent(id))
                    leftover = stageSet;
            }
            String stages = "";
            if (leftover != null)
                for (de.mel.filesync.sql.Stage stage : database.getStageDao().getStagesByStageSetAsList(leftover.getId().v()))
                    stages += " [" + stage.getPath() + stage.getName() + " fs=" + stage.getFsId() + (Boolean.TRUE.equals(stage.getDeleted()) ? " deleted" : "") + "]";
            assertEquals(null, leftover == null ? null : leftover.getId().v() + "/" + leftover.getStatus().v() + stages, "the client staged its own sync writes as changes of the user");
            assertEquals(serverVersion, instances.serverService.getFileSyncDatabaseManager().getLatestVersion(), "the client committed something to the server that nobody changed");
            assertNotNull(database.getFsDao().getGenericByPathAndName("/", "first.bin"));
            assertTrue(synced(instances), "the files are not the same any more");
        }
    }
}

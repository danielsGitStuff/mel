package de.mel.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.mel.auth.service.FileSyncInstances.Snapshot;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.Stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the index that runs when a service boots ({@link de.mel.filesync.index.IndexerRunnable}) finds out about the changes made while the service was down,
 * and hands to the listener. Nothing of it is committed: only the stage set is looked at, as the commit needs it ({@link de.mel.filesync.service.sync.SyncHandler#commitStage}).
 * So these are about the indexer alone, not about merging, propagating or transferring.
 * <p>
 * What the commit needs from a stage set:
 * <ul>
 *     <li>every stage that is not deleted has a content hash, directories too (fswrite.contenthash is NOT NULL)</li>
 *     <li>what the fs table knows has its fs id, so it is updated and not inserted a second time</li>
 *     <li>what is new hangs below a parent: by stage or by fs id</li>
 *     <li>only what changed is in the set. If nothing changed there is no stage set to commit</li>
 * </ul>
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class BootIndexTest {
    @TempDir
    File dir;

    @AfterEach
    public void releaseTheCommit() {
        FileSyncInstances.RecordingServerService.holdBackCommit = false;
    }

    private File serverRoot() {
        return new File(dir, "serverRoot");
    }

    private interface Change {
        void apply(File root) throws Exception;
    }

    /**
     * The server boots with a few files and indexes them. Then it is shut down, the change is made on its disk, and it boots again. What its index found then is returned.
     */
    private Snapshot indexAfter(int port, Change change) throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "changes.bin", 20_000, 1);
        FileSyncInstances.writeFile(serverRoot(), "stays.bin", 21_000, 2);
        FileSyncInstances.writeFile(serverRoot(), "dir/in a dir.bin", 22_000, 3);
        FileSyncInstances.writeFile(serverRoot(), "dir/stays too.bin", 23_000, 4);
        FileSyncInstances.writeFile(serverRoot(), "other/deeper/deepest.bin", 24_000, 5);
        try (FileSyncInstances instances = new FileSyncInstances(dir, port)) {
            instances.createServer();
            assertTrue(TwoInstances.waitFor(() -> !instances.serverService.indexed.isEmpty(), 60), "the server did not index at first boot");
            FileSyncInstances.RecordingServerService.holdBackCommit = true;
            instances.restartServer(() -> {
                try {
                    change.apply(instances.serverRoot);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertTrue(TwoInstances.waitFor(() -> !instances.serverService.indexed.isEmpty(), 60), "the server did not index at the second boot");
            Snapshot snapshot = instances.serverService.indexed.get(0);
            assertEquals(1, instances.serverService.indexed.size(), "the index reported more than once " + instances.serverService.indexed);
            snapshot = Snapshot.of(instances.serverService.getFileSyncDatabaseManager(), snapshot.stageSetId());
            lastKnown = new Known(instances);
            return snapshot;
        }
    }

    /**
     * the fs table as the server had it, for the comparison. Read while the instance is still up.
     */
    private Known lastKnown;

    private class Known {
        final java.util.Map<String, Long> ids = new java.util.HashMap<>();

        Known(FileSyncInstances instances) throws Exception {
            for (String[] f : new String[][]{{"/", "changes.bin"}, {"/", "stays.bin"}, {"/", "dir"}, {"/dir/", "in a dir.bin"}, {"/dir/", "stays too.bin"}, {"/", "other"}, {"/other/", "deeper"}, {"/other/deeper/", "deepest.bin"}}) {
                GenericFSEntry entry = instances.serverService.getFileSyncDatabaseManager().getFsDao().getGenericByPathAndName(f[0], f[1]);
                if (entry != null)
                    ids.put(f[0] + f[1], entry.getId().v());
            }
        }
    }

    /**
     * the invariants every stage set has to keep to be committable
     */
    private static void assertCommittable(Snapshot snapshot) {
        assertNotNull(snapshot.stageSet(), "there is no stage set behind " + snapshot);
        assertTrue(snapshot.staged(), "it is not a staged stage set from the file system: " + snapshot);
        for (Stage stage : snapshot.stages()) {
            if (!Snapshot.deleted(stage))
                assertNotNull(stage.getContentHash(), "the commit writes the content hash of " + stage.getPath() + stage.getName() + " to the fs table, which does not take null. " + snapshot);
            if (stage.getFsId() == null)
                assertTrue(stage.getParentId() != null || stage.getFsParentId() != null, stage.getPath() + stage.getName() + " is new and hangs nowhere. " + snapshot);
        }
    }

    private static void assertOnly(Snapshot snapshot, String... paths) {
        List<String> found = new ArrayList<>();
        for (Stage stage : snapshot.stages())
            found.add(stage.getPath() + stage.getName());
        List<String> expected = List.of(paths);
        assertTrue(found.containsAll(expected) && expected.containsAll(found), "the delta should hold exactly " + expected + " but holds " + found + ": " + snapshot);
    }

    @Test
    public void nothingChangedMeansNothingToCommit() throws Exception {
        Snapshot snapshot = indexAfter(24700, root -> {
        });
        assertTrue(snapshot.stages().isEmpty(), "nothing changed, but the delta is " + snapshot);
    }

    @Test
    public void aFileWhoseContentChangedIsTheWholeDelta() throws Exception {
        Snapshot snapshot = indexAfter(24710, root -> FileSyncInstances.writeFile(root, "changes.bin", 30_000, 9));
        assertCommittable(snapshot);
        Stage stage = snapshot.stage("/", "changes.bin");
        assertNotNull(stage, "the changed file is not in the delta " + snapshot);
        assertEquals(lastKnown.ids.get("/changes.bin"), stage.getFsId());
        assertEquals(30_000L, stage.getSize());
        assertEquals(FileSyncInstances.md5Of(new File(serverRoot(), "changes.bin")), stage.getContentHash());
        assertOnly(snapshot, "/changes.bin");
    }

    @Test
    public void aNewFileInAnExistingDirectoryComesWithItsDirectory() throws Exception {
        Snapshot snapshot = indexAfter(24720, root -> FileSyncInstances.writeFile(root, "dir/new.bin", 2_000, 9));
        assertCommittable(snapshot);
        Stage file = snapshot.stage("/dir/", "new.bin");
        assertNotNull(file, "the new file is not in the delta " + snapshot);
        assertNull(file.getFsId());
        assertEquals(FileSyncInstances.md5Of(new File(serverRoot(), "dir/new.bin")), file.getContentHash());
        Stage directory = snapshot.stage("/", "dir");
        assertNotNull(directory, "the directory has a new content and is part of the delta " + snapshot);
        assertEquals(lastKnown.ids.get("/dir"), directory.getFsId(), "the directory is the one the fs table knows");
        assertTrue(file.getParentId() != null && file.getParentId().equals(directory.getId()) || directory.getFsId().equals(file.getFsParentId()), "the file is below its directory " + snapshot);
        // what is next to it did not change
        assertOnly(snapshot, "/dir", "/dir/new.bin");
    }

    @Test
    public void aNewDirectoryWithAFileInIt() throws Exception {
        Snapshot snapshot = indexAfter(24730, root -> FileSyncInstances.writeFile(root, "brand new/sub/in a new dir.bin", 2_000, 9));
        assertCommittable(snapshot);
        Stage file = snapshot.stage("/brand new/sub/", "in a new dir.bin");
        Stage sub = snapshot.stage("/brand new/", "sub");
        Stage brandNew = snapshot.stage("/", "brand new");
        assertNotNull(file, "" + snapshot);
        assertNotNull(sub, "" + snapshot);
        assertNotNull(brandNew, "" + snapshot);
        assertNull(brandNew.getFsId());
        assertNull(sub.getFsId());
        assertEquals(brandNew.getId(), sub.getParentId(), "sub hangs below brand new " + snapshot);
        assertEquals(sub.getId(), file.getParentId(), "the file hangs below sub " + snapshot);
        // and the root directory, which has a new entry
        assertOnly(snapshot, "", "/brand new", "/brand new/sub", "/brand new/sub/in a new dir.bin");
    }

    @Test
    public void aDeletedFileIsStagedAsDeleted() throws Exception {
        Snapshot snapshot = indexAfter(24740, root -> Files.delete(new File(root, "stays.bin").toPath()));
        assertCommittable(snapshot);
        Stage stage = snapshot.stage("/", "stays.bin");
        assertNotNull(stage, "the deleted file is not in the delta " + snapshot);
        assertTrue(Snapshot.deleted(stage), "" + snapshot);
        assertEquals(lastKnown.ids.get("/stays.bin"), stage.getFsId());
    }

    @Test
    public void aDeletedDirectoryIsStagedAsDeleted() throws Exception {
        Snapshot snapshot = indexAfter(24750, root -> deleteRecursively(new File(root, "other")));
        assertCommittable(snapshot);
        Stage stage = snapshot.stage("/", "other");
        assertNotNull(stage, "the deleted directory is not in the delta " + snapshot);
        assertTrue(Snapshot.deleted(stage), "" + snapshot);
        assertEquals(lastKnown.ids.get("/other"), stage.getFsId());
        // what was in it goes with it: committing a deleted directory removes its entries from the fs table
        assertOnly(snapshot, "", "/other");
    }

    @Test
    public void aFileThatBecameADirectoryAndTheOtherWayRound() throws Exception {
        Snapshot snapshot = indexAfter(24760, root -> {
            Files.delete(new File(root, "stays.bin").toPath());
            FileSyncInstances.writeFile(root, "stays.bin/inside.bin", 1_000, 9);
            deleteRecursively(new File(root, "dir"));
            FileSyncInstances.writeFile(root, "dir", 3_000, 10);
        });
        assertCommittable(snapshot);
        Stage nowDirectory = snapshot.stage("/", "stays.bin");
        Stage nowFile = snapshot.stage("/", "dir");
        assertNotNull(nowDirectory, "" + snapshot);
        assertNotNull(nowFile, "" + snapshot);
        assertTrue(Snapshot.directory(nowDirectory), "" + snapshot);
        assertFalse(Snapshot.directory(nowFile), "" + snapshot);
        assertNotNull(snapshot.stage("/stays.bin/", "inside.bin"), "" + snapshot);
    }

    private static void deleteRecursively(File file) throws Exception {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children)
                deleteRecursively(child);
        Files.delete(file.toPath());
    }
}

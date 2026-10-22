package de.mel.filesync.service.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.RootDirectory;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.FsDirectory;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.StageSet;
import de.mel.filesync.sql.dao.StageDao;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The local stage sets (what the indexer found, one set per run) are merged into one before a sync: the newer edit wins, pairs are found by path and name,
 * what the newer set deleted takes everything below it along, and the result keeps the place of the newest set, so that a set made meanwhile counts as newer.
 */
public class LocalStageSetMergerTest {
    @TempDir
    File dir;

    private FileSyncDatabaseManager databaseManager;
    private StageDao stageDao;
    private LocalStageSetMerger merger;
    private long rootFsId;
    private long order;

    @BeforeEach
    public void before() throws Exception {
        BashTools.Companion.init();
        AbstractFile.configure(new DefaultFileConfiguration());
        File rootFile = new File(dir, "root");
        rootFile.mkdirs();
        RootDirectory rootDirectory = RootDirectory.buildRootDirectory(rootFile);
        FileSyncSettings settings = new FileSyncSettings()
                .setFastBoot(true)
                .setLastSyncedVersion(0L)
                .setMaxAge(30L)
                .setRole(FileSyncStrings.ROLE_CLIENT)
                .setMaxWastebinSize(9999999L)
                .setRootDirectory(rootDirectory)
                .setUseSymLinks(false)
                .setTransferDirectory(AbstractFile.instance(AbstractFile.instance(dir), "transfer.dir"));
        databaseManager = new FileSyncDatabaseManager("test service", dir, settings);
        stageDao = databaseManager.getStageDao();
        FsDirectory root = (FsDirectory) databaseManager.getFsDao().insert(new FsDirectory().setName("root").setVersion(0L).setModified(12L).setDepth(0).setPath(""));
        rootFsId = root.getId().v();
        merger = new LocalStageSetMerger(stageDao);
    }

    private StageSet localSet() throws Exception {
        return stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_FS, FileSyncStrings.STAGESET_STATUS_STAGED, null, null, null, 0L);
    }

    private Stage file(StageSet set, Stage parent, String name, String hash) throws Exception {
        return entry(set, parent, name, false, hash, false);
    }

    private Stage directory(StageSet set, Stage parent, String name, String hash) throws Exception {
        return entry(set, parent, name, true, hash, false);
    }

    private Stage deleted(StageSet set, Stage parent, String name, boolean isDirectory) throws Exception {
        return entry(set, parent, name, isDirectory, "deleted", true);
    }

    /**
     * @param parent a directory of the same set, null for the root directory (it is in the fs table, not in the set)
     */
    private Stage entry(StageSet set, Stage parent, String name, boolean isDirectory, String hash, boolean deleted) throws Exception {
        String path = parent == null ? "/" : parent.getPath() + parent.getName() + "/";
        Stage stage = new Stage()
                .setStageSet(set.getId().v())
                .setName(name)
                .setPath(path)
                .setDepth(parent == null ? 1 : parent.getDepth() + 1)
                .setIsDirectory(isDirectory)
                .setContentHash(hash)
                .setDeleted(deleted)
                .setSynced(true)
                .setVersion(1L)
                .setSize(isDirectory ? null : 100L)
                .setModified(12L)
                .setOrder(order++)
                .setParentId(parent == null ? null : parent.getId())
                .setFsParentId(parent == null ? rootFsId : null);
        return stageDao.insert(stage);
    }

    private static String key(Stage stage) {
        return stage.getPath() + stage.getName() + (stage.getIsDirectory() ? "/" : "");
    }

    /**
     * @return the entries of the set by path, after checking that the set is a proper tree: one entry per path, the parent of an entry is the entry
     * of its directory if the set has one, and comes before it
     */
    private Map<String, Stage> entriesOf(StageSet set) throws Exception {
        List<Stage> stages = stageDao.getStagesByStageSetAsList(set.getId().v());
        Map<String, Stage> byKey = new HashMap<>();
        for (Stage stage : stages)
            assertNull(byKey.put(key(stage), stage), "two entries for " + key(stage) + ": " + stages);
        for (Stage stage : stages) {
            Stage directory = byKey.get(stage.getPath());
            if (directory != null) {
                assertEquals(directory.getId(), stage.getParentId(), key(stage) + " is not below the entry of its directory");
                assertTrue(directory.getOrder() < stage.getOrder(), key(stage) + " comes before its directory");
            } else {
                assertNull(stage.getParentId(), key(stage) + " points to a parent that is not its directory");
            }
        }
        return byKey;
    }

    private StageSet theOneLeft() throws Exception {
        List<StageSet> left = stageDao.getStagedStageSetsFromFS();
        assertEquals(1, left.size(), "not merged into one: " + left);
        return left.get(0);
    }

    @Test
    public void severalSetsBecomeOneThatKeepsThePlaceOfTheNewest() throws Exception {
        StageSet first = localSet();
        file(first, null, "a.txt", "a1");
        StageSet second = localSet();
        file(second, null, "b.txt", "b1");
        StageSet third = localSet();
        file(third, null, "c.txt", "c1");

        StageSet merged = merger.mergeAll(stageDao.getStagedStageSetsFromFS());

        assertEquals(theOneLeft().getId().v(), merged.getId().v());
        assertEquals(third.getId().v(), merged.getId().v(), "the result does not keep the place of the newest set");
        assertEquals(java.util.Set.of("/a.txt", "/b.txt", "/c.txt"), entriesOf(merged).keySet());
    }

    @Test
    public void theNewerEditOfAFileWins() throws Exception {
        file(localSet(), null, "a.txt", "older");
        file(localSet(), null, "a.txt", "newer");

        Map<String, Stage> entries = entriesOf(merger.mergeAll(stageDao.getStagedStageSetsFromFS()));

        assertEquals("newer", entries.get("/a.txt").getContentHash());
    }

    @Test
    public void aFileDeletedInTheNewerSetIsDeletedAndTheOlderChangesNextToItStay() throws Exception {
        StageSet older = localSet();
        file(older, null, "a.txt", "changed");
        file(older, null, "b.txt", "changed too");
        deleted(localSet(), null, "a.txt", false);

        Map<String, Stage> entries = entriesOf(merger.mergeAll(stageDao.getStagedStageSetsFromFS()));

        assertTrue(entries.get("/a.txt").getDeleted(), "the deletion did not win");
        assertNotNull(entries.get("/b.txt"), "a change next to the deleted file got lost");
        assertEquals("changed too", entries.get("/b.txt").getContentHash());
    }

    @Test
    public void aDirectoryDeletedInTheNewerSetTakesTheOlderChangesBelowItAlongAndNothingElse() throws Exception {
        StageSet older = localSet();
        Stage d = directory(older, null, "d", "d1");
        file(older, d, "x.txt", "x changed");
        file(older, null, "other.txt", "other changed");
        deleted(localSet(), null, "d", true);

        Map<String, Stage> entries = entriesOf(merger.mergeAll(stageDao.getStagedStageSetsFromFS()));

        assertTrue(entries.get("/d/").getDeleted(), "the deletion of the directory did not win");
        assertNull(entries.get("/d/x.txt"), "a change below the deleted directory survived");
        assertNotNull(entries.get("/other.txt"), "a change outside the deleted directory got lost");
    }

    @Test
    public void aNewDirectoryWithContentInTheNewerSetKeepsItsTree() throws Exception {
        file(localSet(), null, "a.txt", "a1");
        StageSet newer = localSet();
        Stage n = directory(newer, null, "n", "n1");
        file(newer, n, "f.txt", "f1");

        Map<String, Stage> entries = entriesOf(merger.mergeAll(stageDao.getStagedStageSetsFromFS()));

        assertNotNull(entries.get("/a.txt"));
        assertNotNull(entries.get("/n/f.txt"));
    }

    @Test
    public void aNewDirectoryOfTheOlderSetHoldsWhatBothSetsPutInIt() throws Exception {
        StageSet older = localSet();
        Stage n1 = directory(older, null, "n", "n with f");
        file(older, n1, "f.txt", "f1");
        StageSet newer = localSet();
        // the indexer of the newer run does not know the directory yet either (the older set is not committed): it is new there as well
        Stage n2 = directory(newer, null, "n", "n with f and g");
        file(newer, n2, "g.txt", "g1");

        Map<String, Stage> entries = entriesOf(merger.mergeAll(stageDao.getStagedStageSetsFromFS()));

        assertNotNull(entries.get("/n/f.txt"), "what the older set put in the directory got lost");
        assertNotNull(entries.get("/n/g.txt"));
    }

    @Test
    public void aSetMadeWhileMergingCountsAsNewerThanTheResult() throws Exception {
        StageSet first = localSet();
        file(first, null, "a.txt", "first");
        StageSet second = localSet();
        file(second, null, "a.txt", "second");
        // the indexer finished a run while the two were merged
        StageSet third = localSet();
        file(third, null, "a.txt", "third");

        merger.mergeAll(List.of(first, second));
        Map<String, Stage> entries = entriesOf(merger.mergeAll(stageDao.getStagedStageSetsFromFS()));

        assertEquals("third", entries.get("/a.txt").getContentHash(), "the newest edit lost against the result of an earlier merge");
    }

    @Test
    public void oneSetStaysAsItIs() throws Exception {
        StageSet only = localSet();
        file(only, null, "a.txt", "a1");

        StageSet merged = merger.mergeAll(stageDao.getStagedStageSetsFromFS());

        assertEquals(only.getId().v(), merged.getId().v());
        assertEquals(java.util.Set.of("/a.txt"), entriesOf(merged).keySet());
    }

    @Test
    public void noSetIsNoResult() throws Exception {
        assertNull(merger.mergeAll(stageDao.getStagedStageSetsFromFS()));
    }
}

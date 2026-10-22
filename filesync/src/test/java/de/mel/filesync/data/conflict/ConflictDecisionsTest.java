package de.mel.filesync.data.conflict;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
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
 * An answer to a conflict counts again only for the same two versions: path, name, content hash and deletion of both sides are equal.
 * Every pull makes new stage sets (new ids), so this is what carries an answer from one session to the next. A directory's hash is made of the names in it:
 * its answer counts again only while neither side added, removed or renamed something in it. If the user changed the file again, they are asked again.
 */
public class ConflictDecisionsTest {
    @TempDir
    File dir;

    private FileSyncDatabaseManager databaseManager;
    private StageDao stageDao;
    private long rootFsId;
    private long order;

    @BeforeEach
    public void before() throws Exception {
        BashTools.Companion.init();
        AbstractFile.configure(new DefaultFileConfiguration());
        File rootFile = new File(dir, "root");
        rootFile.mkdirs();
        FileSyncSettings settings = new FileSyncSettings()
                .setFastBoot(true)
                .setLastSyncedVersion(0L)
                .setMaxAge(30L)
                .setRole(FileSyncStrings.ROLE_CLIENT)
                .setMaxWastebinSize(9999999L)
                .setRootDirectory(RootDirectory.buildRootDirectory(rootFile))
                .setUseSymLinks(false)
                .setTransferDirectory(AbstractFile.instance(AbstractFile.instance(dir), "transfer.dir"));
        databaseManager = new FileSyncDatabaseManager("test service", dir, settings);
        stageDao = databaseManager.getStageDao();
        FsDirectory root = (FsDirectory) databaseManager.getFsDao().insert(new FsDirectory().setName("root").setVersion(0L).setModified(12L).setDepth(0).setPath(""));
        rootFsId = root.getId().v();
    }

    private StageSet set(String source) throws Exception {
        return stageDao.createStageSet(source, FileSyncStrings.STAGESET_STATUS_STAGED, 1L, "server", 2L, 1L);
    }

    private Stage entry(StageSet set, String name, boolean isDirectory, String hash) throws Exception {
        return stageDao.insert(new Stage()
                .setStageSet(set.getId().v())
                .setName(name)
                .setPath("/")
                .setDepth(1)
                .setIsDirectory(isDirectory)
                .setContentHash(hash)
                .setDeleted(false)
                .setSynced(true)
                .setVersion(1L)
                .setModified(12L)
                .setOrder(order++)
                .setFsParentId(rootFsId));
    }

    /**
     * @return a solver with the conflicts of the two versions found, after the answers given before were applied
     */
    private ConflictSolver solverFor(boolean isDirectory, String localHash, String remoteHash, Map<String, Boolean> decisions) throws Exception {
        StageSet local = set(FileSyncStrings.STAGESET_SOURCE_FS);
        entry(local, "same", isDirectory, localHash);
        StageSet remote = set(FileSyncStrings.STAGESET_SOURCE_SERVER);
        entry(remote, "same", isDirectory, remoteHash);
        ConflictSolver solver = new ConflictSolver(databaseManager.getConflictDao(), local, remote);
        solver.findConflicts();
        assertTrue(solver.hasConflicts(), "test setup: the two versions should be in conflict");
        solver.applyDecisions(decisions);
        return solver;
    }

    private Map<String, Boolean> answeredLocal(boolean isDirectory, String localHash, String remoteHash) throws Exception {
        ConflictSolver asked = solverFor(isDirectory, localHash, remoteHash, Map.of());
        assertFalse(asked.isSolved());
        asked.getConflictMap().values().forEach(Conflict::decideLocal);
        assertTrue(asked.isSolved());
        return asked.decisions();
    }

    @Test
    public void theSameTwoVersionsOfAFileTakeTheAnswer() throws Exception {
        Map<String, Boolean> decisions = answeredLocal(false, "mine", "theirs");
        ConflictSolver again = solverFor(false, "mine", "theirs", decisions);
        assertTrue(again.isSolved(), "the answer to the same two versions did not count");
        assertTrue(again.getConflictMap().values().iterator().next().getChosenLocal(), "the answer counted the other way round");
    }

    @Test
    public void aFileTheUserChangedAgainIsAskedAgain() throws Exception {
        Map<String, Boolean> decisions = answeredLocal(false, "mine", "theirs");
        assertFalse(solverFor(false, "mine changed again", "theirs", decisions).isSolved(), "an answer about another local version counted");
    }

    @Test
    public void aFileTheServerChangedAgainIsAskedAgain() throws Exception {
        Map<String, Boolean> decisions = answeredLocal(false, "mine", "theirs");
        assertFalse(solverFor(false, "mine", "theirs changed again", decisions).isSolved(), "an answer about another version of the server counted");
    }

    @Test
    public void aDirectoryWithTheSameNamesInItTakesTheAnswer() throws Exception {
        Map<String, Boolean> decisions = answeredLocal(true, "names of mine", "names of theirs");
        assertTrue(solverFor(true, "names of mine", "names of theirs", decisions).isSolved(), "the answer to the same two directories did not count");
    }

    @Test
    public void aDirectoryWithOtherNamesInItIsAskedAgain() throws Exception {
        Map<String, Boolean> decisions = answeredLocal(true, "names of mine", "names of theirs");
        assertFalse(solverFor(true, "names of mine and a new one", "names of theirs", decisions).isSolved(), "an answer about a directory with other content counted");
    }
}

package de.mel.util;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.tools.N;
import de.mel.auth.tools.Order;
import de.mel.execute.SqliteExecutor;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.sql.CreationScripts;
import de.mel.filesync.sql.FsDirectory;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.StageSet;
import de.mel.filesync.sql.dao.ConflictDao;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.sql.SqlQueriesException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.*;

/**
 * find.. methods create conflicts in the stage sets and must find them or must not find them.
 */
public class ConflictTest {
    StageTestCreationDao creationLocalDao;
    StageTestCreationDao creationRemoteDao;
    StageDao stageDao;
    ConflictDao conflictDao;
    FsDao fsDao;
    File dbFile;
    StageSet localStageSet;
    StageSet remoteStageSet;
    ConflictSolver conflictSolver;
    static Integer counter = 0;

    public void fillStageSet(StageTestCreationDao creationDao, long stageSetId) throws SqlQueriesException {
        Order ord = new Order();

        // /
        creationDao.insert(new Stage()
                .setName("root")
                .setIsDirectory(true)
                .setOrder(ord.ord())
                .setContentHash("root")
                .setFsId(0L)
                .setDepth(0)
                .setStageSet(stageSetId)
                .setDeleted(false)
                .setModified(12L)
                .setCreated(12L)
                .setPath(""))
                // /a/
                .insert(stage -> new Stage()
                        .setName("a")
                        .setIsDirectory(true)
                        .setContentHash("a")
                        .setDepth(1)
                        .setPath("/")
                        .setOrder(ord.ord())
                        .setStageSet(stageSetId)
                        .setParentId(stage.getId())
                        .setModified(12L)
                        .setCreated(12L)
                        .setFsParentId(stage.getFsId())
                        .setDeleted(false))
                // /a/aa.txt
                .insert(stage -> new Stage()
                        .setName("aa.txt")
                        .setIsDirectory(false)
                        .setDeleted(false)
                        .setParentId(stage.getId())
                        .setModified(12L)
                        .setCreated(12L)
                        .setOrder(ord.ord())
                        .setContentHash("aa.txt")
                        .setStageSet(stageSetId)
                        .setDepth(2)
                        .setPath("/a/"))
                // /a/
                .up()
                // /
                .up()
                // /b/
                .insert(stage -> new Stage()
                        .setName("b")
                        .setStageSet(stageSetId)
                        .setIsDirectory(true)
                        .setModified(12L)
                        .setCreated(12L)
                        .setOrder(ord.ord())
                        .setDeleted(false)
                        .setDepth(1)
                        .setFsParentId(stage.getFsId())
                        .setParentId(stage.getId())
                        .setContentHash("b")
                        .setPath("/"))
                // /b/bb/
                .insert(stage -> new Stage()
                        .setName("bb")
                        .setIsDirectory(true)
                        .setDeleted(false)
                        .setDepth(2)
                        .setOrder(ord.ord())
                        .setModified(12L)
                        .setCreated(12L)
                        .setParentId(stage.getId())
                        .setContentHash("bb")
                        .setStageSet(stageSetId)
                        .setPath("/b/"))
                // /b/bb/bbb.txt
                .insert(stage -> new Stage()
                        .setName("bbb.txt")
                        .setPath("/b/bb/")
                        .setDepth(3)
                        .setOrder(ord.ord())
                        .setStageSet(stageSetId)
                        .setParentId(stage.getId())
                        .setModified(12L)
                        .setCreated(12L)
                        .setIsDirectory(false)
                        .setDeleted(false)
                        .setContentHash("bbb.txt"));
        // replace separators
        String replacement = N.result(() -> {
            if (File.separator.equals("\\"))
                return "\\\\";
            return File.separator;
        });
        N.forEach(creationDao.getEntries(), stage -> {
            stage.setPath(stage.getPath().replaceAll("\\/", replacement));
            stageDao.update(stage);
        });
    }



    /**
     * this creates two equal stage sets.
     * @throws SqlQueriesException
     * @throws IOException
     * @throws SQLException
     */
    @Before
    public void before() throws SqlQueriesException, IOException, SQLException {
        dbFile = new File("conflict.test." + (counter++) + ".db");
        Lok.debug("testing with db: " + dbFile.getAbsolutePath());
        AbstractFile.configure(new DefaultFileConfiguration());
        BashTools.Companion.init();
        if (dbFile.exists())
            dbFile.delete();
        creationLocalDao = new StageTestCreationDao(dbFile);
        creationRemoteDao = new StageTestCreationDao(creationLocalDao);
        fsDao = new FsDao(null, creationLocalDao.getSqlQueries());
        stageDao = new StageDao(null, creationLocalDao.getSqlQueries(), fsDao);
        conflictDao = new ConflictDao(stageDao, fsDao);

        new SqliteExecutor(creationLocalDao.getSqlQueries().getSQLConnection()).executeStream(CreationScripts.stringToInputStream(new CreationScripts().getCreateFsEntry()));
        new SqliteExecutor(creationLocalDao.getSqlQueries().getSQLConnection()).executeStream(CreationScripts.stringToInputStream(new CreationScripts().getCreateRest()));

        localStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_FS, FileSyncStrings.STAGESET_STATUS_STAGED, null, null, 1L, 0L);
        remoteStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_SERVER, FileSyncStrings.STAGESET_STATUS_STAGED, 1L, "test uuid", 1L, 0L);

        fsDao.insert(new FsDirectory()
                .setName("root")
                .setVersion(0L)
                .setModified(12L)
                .setDepth(0)
                .setPath(""));

        fillStageSet(creationLocalDao, localStageSet.getId().v());
        fillStageSet(creationRemoteDao, remoteStageSet.getId().v());
        Lok.debug("BEFORE");
    }

    /**
     * delete /a/aa.txt remotely
     *
     * @throws SqlQueriesException
     */
    @Test
    public void findDeletedFileConflict() throws SqlQueriesException {
        Stage aatxt = creationRemoteDao.get("aa.txt");
        aatxt.setDeleted(true);
        stageDao.update(aatxt);

        conflictSolver = createConflictSolver().findConflicts();
        assertTrue(conflictSolver.hasConflicts());
        assertEquals(1, conflictSolver.getConflictMap().size());
        assertEquals(1, conflictSolver.getLocalStageConflictMap().size());
        assertEquals(1, conflictSolver.getRemoteStageConflictMap().size());
    }

    /**
     * cofnlict in /a/aa.txt
     *
     * @throws SqlQueriesException
     */
    @Test
    public void findContentFileConflict() throws SqlQueriesException {
        Stage aatxt = creationRemoteDao.get("aa.txt");
        aatxt.setContentHash("changed");
        stageDao.update(aatxt);

        conflictSolver = createConflictSolver().findConflicts();
        assertTrue(conflictSolver.hasConflicts());
        assertEquals(1, conflictSolver.getConflictMap().size());
        assertEquals(1, conflictSolver.getLocalStageConflictMap().size());
        assertEquals(1, conflictSolver.getRemoteStageConflictMap().size());
    }

    private ConflictSolver createConflictSolver() {
        return new ConflictSolver(conflictDao, localStageSet, remoteStageSet);
    }

    /**
     * delete /b/bb/ remotely
     *
     * @throws SqlQueriesException
     */
    @Test
    public void findRemoteDeleted() throws SqlQueriesException {
        Stage bbbtext = creationRemoteDao.get("bbb.txt");
        stageDao.deleteStageById(bbbtext.getId());
        Stage bb = creationRemoteDao.get("bb");
        bb.setDeleted(true);
        stageDao.update(bb);

        conflictSolver = createConflictSolver().findConflicts();
        assertTrue(conflictSolver.hasConflicts());
        assertEquals(1, conflictSolver.getRootConflictMap().size());
        assertEquals(2, conflictSolver.getConflictMap().size());
    }

    @Test
    public void decideContentFileConflictRemote() throws SqlQueriesException {
        findContentFileConflict();
        conflictSolver.getRemoteStageConflictMap().values().forEach(Conflict::decideRemote);
        assertFalse(conflictSolver.hasConflicts());
        conflictSolver.merge();
    }

    @Test
    public void noConflict() throws SqlQueriesException {
        conflictSolver = createConflictSolver().findConflicts();
        conflictSolver.getRemoteStageConflictMap().values().forEach(Conflict::decideRemote);
        assertFalse(conflictSolver.hasConflicts());
    }


    @After
    public void after() throws SqlQueriesException {
        creationLocalDao.cleanUp();
        BashTools.Companion.rmRf(dbFile);
        if (dbFile.exists() && !dbFile.delete()) {
            Lok.error("DB NOT DELETED");
            dbFile.deleteOnExit();
        }
    }
}

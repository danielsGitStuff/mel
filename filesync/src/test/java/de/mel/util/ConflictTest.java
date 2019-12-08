package de.mel.util;

import de.mel.auth.tools.Order;
import de.mel.execute.SqliteExecutor;
import de.mel.filesync.data.FileSyncStrings;
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

import static org.junit.Assert.assertTrue;

public class ConflictTest {
    StageTestCreationDao creationLocalDao;
    StageTestCreationDao creationRemoteDao;
    StageDao stageDao;
    ConflictDao conflictDao;
    FsDao fsDao;
    File dbFile = new File("conflict.test.db");
    StageSet localStageSet;
    StageSet remoteStageSet;

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
                        .setDeleted(false)
                        .setContentHash("bbb.txt"));
    }

    @Before
    public void before() throws SqlQueriesException, IOException, SQLException {
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
    }

    /**
     * delete /b/bb/ remotely
     *
     * @throws SqlQueriesException
     */
    @Test
    public void remoteDeleted() throws SqlQueriesException {
        Stage bbbtext = creationRemoteDao.get("bbb.txt");
        stageDao.deleteStageById(bbbtext.getId());
        Stage bb = creationRemoteDao.get("bb");
        bb.setDeleted(true);
        stageDao.update(bb);

        ConflictSolver conflictSolver = new ConflictSolver(conflictDao, localStageSet, remoteStageSet);
        conflictSolver.findConflicts();
        assertTrue(conflictSolver.hasConflicts());
    }


    @After
    public void after() throws SqlQueriesException {
        creationLocalDao.cleanUp();
    }
}

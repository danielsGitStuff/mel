package de.mel.util;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.tools.N;
import de.mel.auth.tools.Order;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.RootDirectory;
import de.mel.filesync.sql.FileSyncDatabaseManager;
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
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

public class MergeTest {
    static Integer counter = 0;
    StageTestCreationDao creationLocalDao;
    StageTestCreationDao creationRemoteDao;
    StageTestCreationDao creationMergedDao;
    StageDao stageDao;
    ConflictDao conflictDao;
    FsDao fsDao;
    StageSet localStageSet;
    StageSet remoteStageSet;
    FileSyncDatabaseManager databaseManager;
    File workingDirectory;
    FileSyncSettings settings;
    RootDirectory rootDir;
    String SERVICE_ROLE = FileSyncStrings.ROLE_CLIENT;

    /**
     * /a/aa.txt
     * /b/bb/bbb.txt
     * /c/
     *
     * @param creationDao
     * @param stageSetId
     * @throws SqlQueriesException
     */
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
                        .setContentHash("bbb.txt"))
                // /b/bb/
                .up()
                // /b/
                .up()
                // /
                .up()
                .insert(stage -> new Stage()
                        .setName("c")
                        .setDepth(1)
                        .setPath("/")
                        .setOrder(ord.ord())
                        .setStageSet(stageSetId)
                        .setParentId(stage.getId())
                        .setFsParentId(stage.getFsParentId())
                        .setModified(12L)
                        .setIsDirectory(true)
                        .setDeleted(false)
                        .setContentHash("c")
                );
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

    @Test
    public void mergeLocalStageSets() throws SqlQueriesException {

    }

    /**
     * this creates two equal stage sets.
     *
     * @throws SqlQueriesException
     * @throws IOException
     * @throws SQLException
     */
    @Before
    public void before() throws SqlQueriesException, IOException, SQLException, InterruptedException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException, JsonSerializationException, JsonDeserializationException {
        BashTools.Companion.init();
        AbstractFile.configure(new DefaultFileConfiguration());
        workingDirectory = new File("test.workingdir" + counter);
        if (workingDirectory.exists())
            BashTools.Companion.rmRf(workingDirectory);
        workingDirectory.mkdirs();
        AbstractFile.configure(new DefaultFileConfiguration());
        BashTools.Companion.init();

        rootDir = RootDirectory.buildRootDirectory(workingDirectory);
        rootDir.setOriginalFile(AbstractFile.instance(AbstractFile.instance(workingDirectory), "rootdir"));
        rootDir.setPath(workingDirectory.getAbsolutePath());
        settings = new FileSyncSettings()
                .setFastBoot(true)
                .setLastSyncedVersion(0L)
                .setMaxAge(30L)
                .setRole(SERVICE_ROLE)
                .setMaxWastebinSize(9999999L)
                .setRootDirectory(rootDir)
                .setUseSymLinks(false)
                .setTransferDirectory(AbstractFile.instance(AbstractFile.instance(workingDirectory), "transfer.dir"));
        databaseManager = new FileSyncDatabaseManager("dummy service uuid", workingDirectory, settings);

        stageDao = databaseManager.getStageDao();
        fsDao = databaseManager.getFsDao();
        conflictDao = databaseManager.getConflictDao();
        creationLocalDao = new StageTestCreationDao(fsDao.getSqlQueries());
        creationRemoteDao = new StageTestCreationDao(fsDao.getSqlQueries());
        creationMergedDao = new StageTestCreationDao(fsDao.getSqlQueries());

        localStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_FS, FileSyncStrings.STAGESET_STATUS_STAGED, null, null, 1L, 0L);
        remoteStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_SERVER, FileSyncStrings.STAGESET_STATUS_STAGED, 1L, "test uuid", 1L, 0L);

        fsDao.insert(new FsDirectory()
                .setName("root")
                .setVersion(0L)
                .setModified(12L)
                .setDepth(0)
                .setPath(""));

        fillStageSet(creationLocalDao, localStageSet.getId().v());
        Thread.sleep(50L);
        fillStageSet(creationRemoteDao, remoteStageSet.getId().v());
        Lok.debug("BEFORE");
        counter++;
    }

    @After
    public void after() throws SqlQueriesException {
        creationLocalDao.cleanUp();
        BashTools.Companion.rmRf(workingDirectory);
        if (workingDirectory.exists() && !workingDirectory.delete()) {
            Lok.error("DIR NOT DELETED");
            workingDirectory.deleteOnExit();
        }
    }
}

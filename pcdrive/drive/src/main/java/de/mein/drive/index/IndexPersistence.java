package de.mein.drive.index;

import de.mein.auth.tools.NoTryRunner;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.DriveSettings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.SqlQueriesException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 7/11/16.
 */
public class IndexPersistence {

    private final FsDao fsDao;
    private final DriveDatabaseManager databaseManager;
    private final StageDao stageDao;
    private FsDirectory lastDirectory;
    private final DriveSettings driveSettings;
    private Long version = 0l;

    public IndexPersistence(DriveDatabaseManager databaseManager) throws SqlQueriesException {
        this.fsDao = databaseManager.getFsDao();
        this.stageDao = databaseManager.getStageDao();
        this.databaseManager = databaseManager;
        this.driveSettings = databaseManager.getDriveSettings();
        this.version = databaseManager.getFsDao().getLatestVersion() + 1;
    }

    private NoTryRunner runner = new NoTryRunner(Exception::printStackTrace);


    public void foundFile(FsFile fsFile, Long stageSetId) {
        runner.runTry(() -> {
            FsFile dbFile = fsDao.getFileByName(fsFile);
            if (dbFile != null) {
                fsFile.getId().v(dbFile.getId().v());
                if (!dbFile.getContentHash().v().equals(fsFile.getContentHash().v())) {
                    Stage stage = new Stage().setVersion(version)
                            .setName(dbFile.getName().v())
                            .setFsId(dbFile.getId().v())
                            .setFsParentId(dbFile.getParentId().v())
                            .setStageSet(stageSetId)
                            .setIsDirectory(false)
                            .setContentHash(fsFile.getContentHash().v())
                            .setModified(fsFile.getModified().v())
                            .setiNode(fsFile.getiNode().v());
                    stageDao.insert(stage);
                    //fsFile.getVersion().v(dbFile.getVersion().v() + 1);
                    //fsDao.update(fsFile);
                }
            } else {
                Stage stage = new Stage().setVersion(version)
                        .setName(fsFile.getName().v())
                        .setFsId(fsFile.getId().v())
                        .setFsParentId(fsFile.getParentId().v())
                        .setStageSet(stageSetId)
                        .setIsDirectory(false)
                        .setContentHash(fsFile.getContentHash().v())
                        .setModified(fsFile.getModified().v())
                        .setiNode(fsFile.getiNode().v());
                stageDao.insert(stage);
                //fsFile.getVersion().v(version);
                //fsDao.insertLeFile(fsFile);
            }
        });


    }


    public FsDirectory roamDirectory(FsDirectory parent, FsDirectory fsDirectory, long stageSetId) throws SqlQueriesException {
        FsDirectory dbDirectory = fsDao.getSubDirectory(parent, fsDirectory);
        if (dbDirectory == null) {
            fsDirectory.getVersion().v(version);
            /*Stage stage = new Stage()
                    .setName(fsDirectory.getName().v())
                    .setFsId(fsDirectory.getId().v())
                    .setFsParentId(fsDirectory.getParentId().v())
                    .setStageSet(stageSetId)
                    .setContentHash(fsDirectory.getContentHash().v())
                    .setiNode(fsDirectory.getiNode().v())
                    .setModified(fsDirectory.getModified().v())
                    .setIsDirectory(fsDirectory.getIsDirectory().v());
            stageDao.insert(stage);*/
            //fsDao.insertLeDirectory(fsDirectory);
            dbDirectory = fsDao.getDirectoryById(fsDirectory.getId().v());
        } else            //skip the root itself

            fsDirectory.getId().v(dbDirectory.getId());
        return dbDirectory;
    }


    public void done() {
        System.out.println("Indexer.main(...).new ICrawlerListener() {...}.done()");
    }

/*
    public void doneWith(FsDirectory leDirectory) {
        runner.runTry(() -> {
            FsDirectory dbDirectory = fsDao.getDirectoryById(leDirectory.getId().v());
            fsDao.update(leDirectory);
        });

    }*/

/*
    public void updateDirectory(FsDirectory dbDirectory, FsDirectory actualDirectory, long stageSetId) throws SqlQueriesException, IllegalAccessException, IOException, JsonSerializationException {
        //System.out.println((dbDirectory == null) + " " + (actualDirectory == null) + " " + actualDirectory.getOriginal().getAbsolutePath() + " " + actualDirectory.getClass().getSimpleName());
        boolean diff = (actualDirectory instanceof RootDirectory && ((RootDirectory) actualDirectory).diff())
                || (dbDirectory != null && !dbDirectory.getContentHash().ignoreListener().v().equals(actualDirectory.getContentHash().v()));
        if (diff) {
            //find deleted stuff
            Long directoryId;
            if (actualDirectory instanceof RootDirectory)
                directoryId = null;
            else
                directoryId = actualDirectory.getId().v();

            List<FsDirectory> dbSubdirs = fsDao.getSubDirectoriesByParentId(directoryId);
            List<FsFile> dbFiles = fsDao.getFilesByFsDirectory(directoryId);
            Map<String, FsDirectory> nameSubDirMap = new HashMap<>();
            for (FsDirectory subdir : dbSubdirs) {
                nameSubDirMap.put(subdir.getName().v(), subdir);
            }
            Map<String, FsFile> nameFileMap = new HashMap<>();
            for (FsFile fsFile : dbFiles) {
                nameFileMap.put(fsFile.getName().v(), fsFile);
            }
            // kick everything out that is still there
            for (FsDirectory subdir : actualDirectory.getSubDirectories()) {
                nameSubDirMap.remove(subdir.getName().v());
            }
            for (FsFile file : actualDirectory.getFiles()) {
                nameFileMap.remove(file.getName().v());
            }
            // delete the rest
            for (FsDirectory subdir : nameSubDirMap.values()) {
                Stage stage = new Stage().setFsId(subdir.getId().v())
                        .setFsParentId(subdir.getParentId().v())
                        .setName(subdir.getName().v())
                        .setStageSet(stageSetId)
                        .setDeleted(true)
                        .setIsDirectory(true);
                stageDao.insert(stage);
                //fsDao.delete(subdir);
            }
            for (FsFile file : nameFileMap.values()) {
                Stage stage = new Stage().setFsId(file.getId().v())
                        .setFsParentId(file.getParentId().v())
                        .setName(file.getName().v())
                        .setStageSet(stageSetId)
                        .setDeleted(true)
                        .setIsDirectory(false);
                stageDao.insert(stage);
                //fsDao.delete(file);
            }
            //store
            actualDirectory.getVersion().v(version);
            if (actualDirectory instanceof RootDirectory) {
                driveSettings.save();
                //((RootDirectory) actualDirectory).save();
            } else {
                Stage stage = new Stage().setFsId(actualDirectory.getId().v())
                        .setFsParentId(actualDirectory.getParentId().v())
                        .setName(actualDirectory.getName().v())
                        .setStageSet(stageSetId)
                        .setDeleted(true)
                        .setIsDirectory(true);
                stageDao.insert(stage);
                //fsDao.update(actualDirectory);
            }
        }
    }
*/
    public Stage stage(FsDirectory dbDirectory, FsDirectory actualDirectory, Stage parentStage, long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage().setIsDirectory(true)
                .setName(actualDirectory.getName().v())
                .setStageSet(stageSetId)
                .setContentHash(actualDirectory.getContentHash().v())
                .setiNode(actualDirectory.getiNode().v())
                .setModified(actualDirectory.getModified().v());
        if (dbDirectory != null) {
            stage.setFsId(dbDirectory.getId().v())
                    .setFsParentId(dbDirectory.getParentId().v())
                    .setVersion(dbDirectory.getVersion().v());
        }
        if (parentStage!=null)
            stage.setParentId(parentStage.getId());
        stageDao.insert(stage);
        return stage;
    }
}

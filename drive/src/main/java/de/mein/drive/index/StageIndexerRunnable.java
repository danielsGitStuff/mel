package de.mein.drive.index;

import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Deferred;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by xor on 11/24/16.
 */
public class StageIndexerRunnable implements Runnable {
    private final DriveDatabaseManager databaseManager;
    private final Long stageSetId;
    private final StageDao stageDao;
    private final FsDao fsDao;
    private Deferred<Long, Exception, Void> promise = new DeferredObject<>();

    public StageIndexerRunnable(DriveDatabaseManager databaseManager, Long stageSetId) {
        this.databaseManager = databaseManager;
        this.stageDao = databaseManager.getStageDao();
        this.fsDao = databaseManager.getFsDao();
        this.stageSetId = stageSetId;
    }

    private String buildPathFromStage2(Stage stage) throws SqlQueriesException {
        String res = "";
        if (stage.getFsParentId() != null) {
            FsDirectory fsParent = fsDao.getDirectoryById(stage.getFsParentId());
            if (fsParent.isRoot())
                return databaseManager.getDriveSettings().getRootDirectory().getPath() + File.separator + stage.getName();
            res = fsDao.getFileByFsFile(databaseManager.getDriveSettings().getRootDirectory(), fsParent).getAbsolutePath();
            res += File.separator + stage.getName();
        } else if (stage.getParentId() != null) {
            Stage parentStage = stageDao.getStageById(stage.getParentId());
            return buildPathFromStage2(parentStage) + File.separator + stage.getName();
        } else if (stage.getFsId() != null) {
            FsDirectory fs = fsDao.getDirectoryById(stage.getFsId());
            if (fs.isRoot())
                return databaseManager.getDriveSettings().getRootDirectory().getPath();
            res = "2";
        }
        return res;
    }

    private String buildPathFromStage(String name, Long parentId, Long parentFsId, Stage stage) throws SqlQueriesException {
        return buildPathFromStage2(stage);

    }

    @Override
    public void run() {
        try {
            final String rootPath = databaseManager.getDriveSettings().getRootDirectory().getPath();
            List<Stage> stages = stageDao.getStagesByStageSet(stageSetId);
            for (Stage stage : stages) {
                // build a File
                String path = buildPathFromStage(stage.getName(), stage.getParentId(), stage.getFsParentId(), stage);
                File f = new File(path);
                if (stage.getIsDirectory()) {
                    roamDirectoryStage(stage, f);
                } else {
                    this.updateFileStage(stage, f);
                    stageDao.update(stage);
                }
                System.out.println("StageIndexerRunnable.run: " + path);
            }
            System.out.println("StageIndexerRunnable.runTry(" + stageSetId + ").finished");
            promise.resolve(stageSetId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void roamDirectoryStage(Stage stage, File stageFile) throws SqlQueriesException, IOException {
        FsDirectory fsDirectory = new FsDirectory();
        // roam directory if necessary
        File[] files = stageFile.listFiles(File::isFile);
        for (File subFile : files) {
            fsDirectory.addFile(new FsFile(subFile));
            // check if which subFiles are on stage or fs. if not, index them
            Stage subStage = stageDao.getStageByStageSetParentName(stageSetId, stage.getId(), subFile.getName());
            FsFile subFsFile = fsDao.getFileByName(stage.getFsId(), subFile.getName());
            if (subStage == null && subFsFile == null) {
                // stage
                subStage = new Stage().setName(subFile.getName())
                        .setIsDirectory(false)
                        .setParentId(stage.getId())
                        .setStageSet(stageSetId)
                        .setDeleted(false);
                this.updateFileStage(subStage, subFile);
                stageDao.insert(subStage);
            }
        }
        File[] subDirs = stageFile.listFiles(File::isDirectory);
        for (File subDir : subDirs) {
            if (subDir.getAbsolutePath().equals(databaseManager.getDriveSettings().getTransferDirectoryPath()))
                continue;
            // if subDir is on stage or fs we don't have to roam it
            Stage subStage = stageDao.getStageByStageSetParentName(stageSetId, stage.getId(), subDir.getName());
            FsDirectory leSubDirectory = null;
            if (stage.getFsId() != null)
                leSubDirectory = fsDao.getSubDirectoryByName(stage.getFsId(), subDir.getName());
            if (subStage == null && leSubDirectory == null) {
                // roam
                subStage = new Stage().setStageSet(stageSetId)
                        .setParentId(stage.getId())
                        .setName(subDir.getName())
                        .setIsDirectory(true);
                System.out.println("StageIndexerRunnable.roamDirectoryStage.roam sub: " + subDir.getAbsolutePath());
                stageDao.insert(subStage);
                roamDirectoryStage(subStage, subDir);
            }
            if (leSubDirectory == null)
                leSubDirectory = new FsDirectory(subDir);
            fsDirectory.addSubDirectory(leSubDirectory);
        }
        // save to stage
        fsDirectory.calcContentHash();
        stage.setContentHash(fsDirectory.getContentHash().v());
        BashTools.NodeAndTime nodeAndTime = BashTools.getNodeAndTime(stageFile);
        stage.setModified(nodeAndTime.getModifiedTime())
                .setiNode(nodeAndTime.getInode());
        stageDao.update(stage);
    }

    private void updateFileStage(Stage stage, File stageFile) throws IOException {
        if (stageFile.exists()) {
            BashTools.NodeAndTime nodeAndTime = BashTools.getNodeAndTime(stageFile);
            stage.setContentHash(Hash.md5(stageFile));
            stage.setiNode(nodeAndTime.getInode());
            stage.setModified(nodeAndTime.getModifiedTime());
            stage.setSize(stageFile.length());
        } else {
            stage.setDeleted(true);
        }
    }

    public Deferred<Long, Exception, Void> getPromise() {
        return promise;
    }
}

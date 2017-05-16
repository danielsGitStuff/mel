package de.mein.drive.watchdog;

import de.mein.auth.tools.Hash;
import de.mein.auth.tools.Order;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.PathCollection;
import de.mein.drive.index.BashTools;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Locks fsDao for reading
 * Created by xor on 11/24/16.
 */
public class StageIndexerRunnable implements Runnable {
    private final DriveDatabaseManager databaseManager;
    private Long stageSetId;
    private StageSet stageSet;
    private final StageDao stageDao;
    private final FsDao fsDao;
    private final PathCollection pathCollection;
    private StageIndexer.StagingDoneListener stagingDoneListener;
    private Order order = new Order();
//    private Deferred<StageIndexerRunnable, Exception, Void> promise = new DeferredObject<>();

    public StageIndexerRunnable(DriveDatabaseManager databaseManager, PathCollection pathCollection) {
        this.databaseManager = databaseManager;
        this.stageDao = databaseManager.getStageDao();
        this.fsDao = databaseManager.getFsDao();
        this.pathCollection = pathCollection;
    }

    public Long getStageSetId() {
        return stageSetId;
    }

    public FsDao getFsDao() {
        return fsDao;
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


    protected void initStage(PathCollection pathCollection) throws IOException, SqlQueriesException {
//        stageDao.lockWrite();
        try {
            stageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_FS, null, null);
            this.stageSetId = stageSet.getId().v();
            for (String path : pathCollection.getPaths()) {
                try {
                    File f = new File(path);
                    File parent = f.getParentFile();
                    FsDirectory fsParent = null;
                    FsEntry fsEntry = null;
                    Stage stage;
                    Stage stageParent = stageDao.getStageByPath(stageSet.getId().v(), parent);
                    // find the actual relating FsEntry of the parent directory
                    fsParent = fsDao.getFsDirectoryByPath(parent);
                    // find its relating FsEntry
                    if (fsParent != null) {
                        GenericFSEntry genDummy = new GenericFSEntry();
                        genDummy.getParentId().v(fsParent.getId());
                        genDummy.getName().v(f.getName());
                        GenericFSEntry gen = fsDao.getGenericFileByName(genDummy);
                        if (gen != null) {
                            fsEntry = gen.ins();
                        }
                    } else {
                        System.err.println("klc9004p,");
                        System.err.println("parent, could not find a corresponding FSEntry to this Stage:");
                        System.err.println("id " + stageParent.getId() + " fsid " + stageParent.getFsId()
                                + " parentid " + stageParent.getParentId() + " parentfsid " + stageParent.getFsParentId()
                                + " name " + stageParent.getName());
                    }
                    //file might been deleted yet :(
                    if (!f.exists() && fsEntry == null)
                        continue;
                    // stage actual File
                    stage = new Stage().setName(f.getName()).setIsDirectory(f.isDirectory());
                    if (fsEntry != null) {
                        stage.setFsId(fsEntry.getId().v()).setFsParentId(fsEntry.getParentId().v());
                    }
                    if (fsParent != null) {
                        stage.setFsParentId(fsParent.getId().v());
                    }
                    // we found everything which already exists in das datenbank
                    if (stageParent == null) {
                        stageParent = new Stage().setStageSet(stageSet.getId().v());
                        if (fsParent == null) {
                            System.err.println("zsrg44gxths");
                            stageParent.setIsDirectory(parent.isDirectory());
                        } else {
                            stageParent.setName(fsParent.getName().v())
                                    .setFsId(fsParent.getId().v())
                                    .setFsParentId(fsParent.getParentId().v())
                                    .setStageSet(stageSet.getId().v())
                                    .setVersion(fsParent.getVersion().v())
                                    .setIsDirectory(fsParent.getIsDirectory().v());
                            File exists = fsDao.getFileByFsFile(databaseManager.getDriveSettings().getRootDirectory(), fsParent);
                            stageParent.setDeleted(!exists.exists());
                        }
                        stageParent.setOrder(order.ord());
                        stageDao.insert(stageParent);
                    }
                    stage.setParentId(stageParent.getId());
                    if (fsParent != null) {
                        stage.setFsParentId(fsParent.getId().v());
                    }
                    if (fsEntry != null) {
                        stage.setFsId(fsEntry.getId().v());
                        stage.setVersion(fsEntry.getVersion().v());
                    }
                    stage.setStageSet(stageSet.getId().v());
                    stage.setDeleted(!f.exists());
                    if (!stage.getIsDirectory())
                        stage.setSynced(true);
                    stage.setOrder(order.ord());
                    stageDao.insert(stage);
                } catch (Exception e) {
                    System.err.println("MeinDriveServerService.doFsSyncJob: " + path);
                    e.printStackTrace();
                }
            }
            // done here. set the indexer to work
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //stageDao.deleteStageSet(stageSet.getId().v());
//            fsDao.unlockRead();
//            stageDao.unlockWrite();
        }
    }

    @Override
    public void run() {
        try {
            fsDao.lockRead();
            initStage(pathCollection);

            final String rootPath = databaseManager.getDriveSettings().getRootDirectory().getPath();
            ISQLResource<Stage> stages = stageDao.getStagesByStageSet(stageSetId);
            Stage stage = stages.getNext();
            while (stage != null) {
                // build a File
                String path = buildPathFromStage(stage.getName(), stage.getParentId(), stage.getFsParentId(), stage);
                File f = new File(path);
                if (stage.getIsDirectory()) {
                    roamDirectoryStage(stage, f);
                } else {
                    this.updateFileStage(stage, f);
                }
                System.out.println("StageIndexerRunnable.run: " + path);
                stage = stages.getNext();
            }
            System.out.println("StageIndexerRunnable.runTry(" + stageSetId + ").finished");
            stageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
            stageDao.updateStageSet(stageSet);
            stagingDoneListener.onStagingFsEventsDone(stageSetId);
//            promise.resolve(this);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            fsDao.unlockRead();
        }
    }

    private void roamDirectoryStage(Stage stage, File stageFile) throws SqlQueriesException, IOException {
        FsDirectory newFsDirectory = new FsDirectory();
        // roam directory if necessary
        File[] files = stageFile.listFiles(File::isFile);
        //todo debug
        if (files == null)
            System.out.println("StageIndexerRunnable.roamDirectoryStage");
        for (File subFile : files) {
            newFsDirectory.addFile(new FsFile(subFile));
            // check if which subFiles are on stage or fs. if not, index them
            Stage subStage = stageDao.getStageByStageSetParentName(stageSetId, stage.getId(), subFile.getName());
            FsFile subFsFile = fsDao.getFileByName(stage.getFsId(), subFile.getName());
            if (subStage == null && subFsFile == null) {
                // stage
                subStage = new Stage().setName(subFile.getName())
                        .setIsDirectory(false)
                        .setParentId(stage.getId())
                        .setFsParentId(stage.getFsId())
                        .setStageSet(stageSetId)
                        .setDeleted(false);
                this.updateFileStage(subStage, subFile);
                subStage.setOrder(order.ord());
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
                subStage.setOrder(order.ord());
                stageDao.insert(subStage);
                roamDirectoryStage(subStage, subDir);
            }
            if (leSubDirectory == null)
                leSubDirectory = new FsDirectory(subDir);
            newFsDirectory.addSubDirectory(leSubDirectory);
        }
        // add not yet synced files to newStage
        if (stage.getFsId() != null) {
            List<FsFile> notSyncedFiles = fsDao.getNonSyncedFilesByFsDirectory(stage.getFsId());
            for (FsFile notSyncedFile : notSyncedFiles) {
                newFsDirectory.addFile(notSyncedFile);
            }
        }
        // save to stage
        newFsDirectory.calcContentHash();
        stage.setContentHash(newFsDirectory.getContentHash().v());
        BashTools.NodeAndTime nodeAndTime = BashTools.getNodeAndTime(stageFile);
        stage.setModified(nodeAndTime.getModifiedTime())
                .setiNode(nodeAndTime.getInode());
        if (stage.getFsId() != null) {
            FsDirectory oldFsDirectory = fsDao.getFsDirectoryById(stage.getFsId());
            if (oldFsDirectory.getContentHash().v().equals(newFsDirectory.getContentHash().v()))
                stageDao.deleteStageById(stage.getId());
            else
                stageDao.update(stage);
        } else
            stageDao.update(stage);
    }

    private void updateFileStage(Stage stage, File stageFile) throws IOException, SqlQueriesException {
        if (stageFile.exists()) {
            BashTools.NodeAndTime nodeAndTime = BashTools.getNodeAndTime(stageFile);
            stage.setContentHash(Hash.md5(stageFile));
            stage.setiNode(nodeAndTime.getInode());
            stage.setModified(nodeAndTime.getModifiedTime());
            stage.setSize(stageFile.length());
            // stage can be deleted if nothing changed
            if (stage.getFsId() != null) {
                FsEntry fsEntry = fsDao.getFile(stage.getFsId());
                if (fsEntry.getContentHash().v().equals(stage.getContentHash()))
                    stageDao.deleteStageById(stage.getId());
                else
                    stageDao.update(stage);
            } else
                stageDao.update(stage);

        } else {
            stage.setDeleted(true);
            stageDao.update(stage);
        }
    }

    public void setStagingDoneListener(StageIndexer.StagingDoneListener stagingDoneListener) {
        assert stagingDoneListener != null;
        this.stagingDoneListener = stagingDoneListener;
    }

//    public Deferred<StageIndexerRunnable, Exception, Void> getPromise() {
//        return promise;
//    }
}

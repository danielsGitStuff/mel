package de.mein.drive.index;

import de.mein.core.Hash;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.PathCollection;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsEntry;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
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
    private final StageDao stageDao;
    private final FsDao fsDao;
    private final PathCollection pathCollection;
    private StageIndexer.StagingDoneListener stagingDoneListener;
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
        StageSet stageSet;
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
                    if (stageParent == null) {
                        // find the actual relating FsEntry of the parent directory
                        fsParent = fsDao.getFsDirectoryByPath(parent);
                        // find its relating FsEntry
                        if (fsParent != null) {
                            GenericFSEntry genDummy = new GenericFSEntry();
                            genDummy.getParentId().v(fsParent.getId());
                            genDummy.getName().v(f.getName());
                            GenericFSEntry gen = fsDao.getGenericFileByName(genDummy);
                            if (gen != null)
                                fsEntry = gen.ins();
                        } else {
                            System.err.println("klc9004p,");
                        }
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
                        stageDao.insert(stageParent);
                    }
                    stage.setParentId(stageParent.getId());
                    if (fsParent == null)
                        stage.setParentId(stageParent.getId());
                    stage.setStageSet(stageSet.getId().v());
                    stage.setDeleted(!f.exists());
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
                    stageDao.update(stage);
                }
                System.out.println("StageIndexerRunnable.run: " + path);
                stage = stages.getNext();
            }
            System.out.println("StageIndexerRunnable.runTry(" + stageSetId + ").finished");
            stagingDoneListener.onStagingDone(stageSetId);
//            promise.resolve(this);
        } catch (Exception e) {
            e.printStackTrace();
            fsDao.unlockRead();
        }finally {
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
            stage.setContentHash(de.mein.core.Hash.md5(stageFile));
            stage.setiNode(nodeAndTime.getInode());
            stage.setModified(nodeAndTime.getModifiedTime());
            stage.setSize(stageFile.length());
        } else {
            stage.setDeleted(true);
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

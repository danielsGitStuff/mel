package de.mein.drive.index;

import de.mein.DeferredRunnable;
import de.mein.auth.tools.Hash;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.ISQLResource;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Created by xor on 5/18/17.
 */
@SuppressWarnings("Duplicates")
public abstract class AbstractIndexer extends DeferredRunnable {
    protected final DriveDatabaseManager databaseManager;
    protected final StageDao stageDao;
    protected final FsDao fsDao;
    private final String serviceName;
    protected StageSet stageSet;
    protected Long stageSetId;
    private Order order = new Order();


    protected AbstractIndexer(DriveDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.stageDao = databaseManager.getStageDao();
        this.fsDao = databaseManager.getFsDao();
        this.serviceName = databaseManager.getMeinDriveService().getRunnableName();
    }

    @Override
    public void onShutDown() {
        System.out.println(getClass().getSimpleName() + " for " + serviceName + ".onShutDown");
    }

    protected String buildPathFromStage(Stage stage) throws SqlQueriesException {
        String res = "";
        if (stage.getFsParentId() != null) {
            FsDirectory fsParent = fsDao.getDirectoryById(stage.getFsParentId());
            if (fsParent.isRoot())
                return databaseManager.getDriveSettings().getRootDirectory().getPath() + File.separator + stage.getName();
            res = fsDao.getFileByFsFile(databaseManager.getDriveSettings().getRootDirectory(), fsParent).getAbsolutePath();
            res += File.separator + stage.getName();
        } else if (stage.getParentId() != null) {
            Stage parentStage = stageDao.getStageById(stage.getParentId());
            return buildPathFromStage(parentStage) + File.separator + stage.getName();
        } else if (stage.getFsId() != null) {
            FsDirectory fs = fsDao.getDirectoryById(stage.getFsId());
            if (fs.isRoot())
                return databaseManager.getDriveSettings().getRootDirectory().getPath();
            res = "2";
        }
        return res;
    }

    protected void examineStage() throws SqlQueriesException, IOException {
        ISQLResource<Stage> stages = stageDao.getStagesByStageSet(stageSetId);
        Stage stage = stages.getNext();
        while (stage != null) {
            // build a File
            String path = buildPathFromStage(stage);
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
    }


    protected void initStage(String stageSetType, Stream<String> paths) throws IOException, SqlQueriesException {
//        stageDao.lockWrite();
        try {
            stageSet = stageDao.createStageSet(stageSetType, null, null);
            String path = "none yet";
            this.stageSetId = stageSet.getId().v();
            Iterator<String> iterator = paths.iterator();
            while (iterator.hasNext()) {
                try {
                    path = iterator.next();
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
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + serviceName;
    }

    protected void roamDirectoryStage(Stage stage, File stageFile) throws SqlQueriesException, IOException {
        if (stage.getIsDirectory() && stage.getDeleted())
            return;
        FsDirectory newFsDirectory = new FsDirectory();
        // roam directory if necessary
        File[] files = stageFile.listFiles(File::isFile);
        File[] subDirs = stageFile.listFiles(File::isDirectory);
        if (files == null || subDirs == null)
            System.out.println("AbstractIndexer.roamDirectoryStage.dbuer903tj");
        // map will contain all FsEntry that must be deleted
        Map<String, GenericFSEntry> fsContent = new HashMap<>();
        if (stage.getFsId() != null) {
            List<GenericFSEntry> generics = fsDao.getContentByFsDirectory(stage.getFsId());
            for (GenericFSEntry gen : generics)
                fsContent.put(gen.getName().v(), gen);
        }
        // remove deleted stuff first (because of the order)
        if (files != null) {
            for (File subFile : files) {
                fsContent.remove(subFile.getName());
            }
        }
        if (subDirs != null) {
            for (File subDir : subDirs) {
                fsContent.remove(subDir.getName());
            }
        }
        for (String name : fsContent.keySet()) {
            GenericFSEntry gen = fsContent.get(name);
            Stage delStage = GenericFSEntry.generic2Stage(gen, stageSetId)
                    .setDeleted(true)
                    .setOrder(order.ord())
                    .setVersion(gen.getVersion().v())
                    .setiNode(gen.getiNode().v())
                    .setModified(gen.getModified().v())
                    .setSynced(gen.getSynced().v());

            stageDao.insert(delStage);
        }
        if (files != null) {
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
        }
        for (File subDir : subDirs) {
            if (subDir.getAbsolutePath().equals(databaseManager.getDriveSettings().getTransferDirectoryPath()))
                continue;
            fsContent.remove(subDir.getName());
            // if subDir is on stage or fs we don't have to roam it
            Stage subStage = stageDao.getStageByStageSetParentName(stageSetId, stage.getId(), subDir.getName());
            FsDirectory leSubDirectory = null;
            if (stage.getFsId() != null)
                leSubDirectory = fsDao.getSubDirectoryByName(stage.getFsId(), subDir.getName());
            if (subStage == null && leSubDirectory == null) {
                // roam
                subStage = new Stage().setStageSet(stageSetId)
                        .setParentId(stage.getId())
                        .setFsParentId(stage.getFsId())
                        .setName(subDir.getName())
                        .setIsDirectory(true)
                        .setDeleted(!subDir.exists());
                System.out.println("StageIndexerRunnable.roamDirectoryStage.roam sub: " + subDir.getAbsolutePath());
                subStage.setOrder(order.ord());
                //todo debug
                if (subStage.getDeleted() == true && subStage.getName().equals("samesub"))
                    System.out.println("AbstractIndexer.roamDirectoryStage");
                if (subStage.getDeleted() == null)
                    System.out.println("AbstractIndexer.roamDirectoryStage.debugemsagß5");
                try {
                    stageDao.insert(subStage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
        RWLock waitLock = new RWLock().lockWrite();
        Promise<BashTools.NodeAndTime, Exception, Void> promise = BashTools.getNodeAndTime(stageFile);
        promise.done(nodeAndTime -> N.r(() -> {
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
            waitLock.unlockWrite();
        }));
        waitLock.lockWrite();
    }

    protected void updateFileStage(Stage stage, File stageFile) throws IOException, SqlQueriesException {
        if (stageFile.exists()) {
            RWLock waitLock = new RWLock().lockWrite();
            Promise<BashTools.NodeAndTime, Exception, Void> promise = BashTools.getNodeAndTime(stageFile);
            promise.done(nodeAndTime -> N.r(() -> {
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
                waitLock.unlockWrite();
            }));
            waitLock.lockWrite();
        } else {
            stage.setDeleted(true);
            stageDao.update(stage);
        }
    }
}

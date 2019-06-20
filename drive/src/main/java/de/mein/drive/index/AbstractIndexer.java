package de.mein.drive.index;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.core.serialize.serialize.tools.OTimer;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.FsBashDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.watchdog.IndexWatchdogListener;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.Hash;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
    protected Boolean fastBooting = true;


    protected AbstractIndexer(DriveDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        fastBooting = databaseManager.getDriveSettings().getFastBoot();
        this.stageDao = databaseManager.getStageDao();
        this.fsDao = databaseManager.getFsDao();
        this.serviceName = databaseManager.getMeinDriveService().getRunnableName();
    }

    @Override
    public void onShutDown() {
        Lok.debug(getClass().getSimpleName() + "[" + stageSetId + "] for " + serviceName + ".onShutDown");
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
            Lok.error("this method failed");
            res = "2";
        }
        return res;
    }

    /**
     * Compresses the {@link StageSet} to a delta of the current {@link FsEntry}s.
     * If there is no difference and the {@link StageSet} is empty it is deleted.
     *
     * @throws SqlQueriesException
     * @throws IOException
     */
    protected void examineStage() throws SqlQueriesException, IOException {
        OTimer timer = new OTimer("examine all");
        OTimer timer1 = new OTimer("examine 1");
        OTimer timer2 = new OTimer("examine 2");
        OTimer timer3 = new OTimer("examine 2");
        OTimer timerUpdate1 = new OTimer("updateFileStage.internal.1");
        OTimer timerUpdate2 = new OTimer("updateFileStage.internal.2");
        timer.start();
        N.sqlResource(stageDao.getStagesByStageSet(stageSetId), stages -> {
            Stage stage = stages.getNext();
            while (stage != null) {
                // build a File
                timer1.start();
                String path = buildPathFromStage(stage);
                AFile f = AFile.instance(path);
                timer1.stop();
                if (stage.getIsDirectory()) {
                    timer2.start();
                    roamDirectoryStage(stage, f);
                    timer2.stop();
                } else {
                    timer3.start();
                    this.updateFileStage(stage, f, timerUpdate1, timerUpdate2, null);
                    timer3.stop();
                }
                stage = stages.getNext();
            }
        });
        stageDao.deleteMarkedForRemoval(stageSet.getId().v());
        Lok.debug("StageIndexerRunnable.runTry(" + stageSetId + ").finished");
        stageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(stageSet);
        if (!stageDao.stageSetHasContent(stageSetId)) {
            stageDao.deleteStageSet(stageSetId);
            stageSetId = null;
        }
        timer.stop().print();
        timer1.print();
        timer2.print();
        timer3.print();
        timerUpdate1.print();
        timerUpdate2.print();
    }


    protected void initStage(String stageSetType, Iterator<AFile<?>> iterator, IndexWatchdogListener indexWatchdogListener) throws IOException, SqlQueriesException {
        OTimer timer = new OTimer("initStage().connect2fs");
        OTimer timerInternal1 = new OTimer("initStage.internal.1");
        OTimer timerInternal2 = new OTimer("initStage.internal.2");


        stageSet = stageDao.createStageSet(stageSetType, null, null, null);
        final int rootPathLength = databaseManager.getDriveSettings().getRootDirectory().getPath().length();
        this.stageSetId = stageSet.getId().v();

        IndexHelper indexHelper = new IndexHelper(databaseManager, stageSetId, order);
        while (iterator.hasNext()) {
            AFile f = iterator.next();
            AFile parent = f.getParentFile();
            FsDirectory fsParent = null;
            FsEntry fsEntry = null;
            Stage stage;

            timerInternal1.start();

            try {
                if (BashTools.isSymLink(f)) {
                    if (!databaseManager.getDriveSettings().getUseSymLinks())
                        continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // find the actual relating FsEntry of the parent directory
            // android does not recognize --mindepth when calling find. if we find the root directory here we must skip it.
            if (parent == null || parent.getAbsolutePath().length() < rootPathLength)
                Lok.debug("AbstractIndexer.initStage. find ignored --mindepth. fixed it");
            else
                fsParent = fsDao.getFsDirectoryByPath(parent);
            // find its relating FsEntry
            if (fsParent != null) {
                GenericFSEntry genParentDummy = new GenericFSEntry();
                genParentDummy.getParentId().v(fsParent.getId());
                genParentDummy.getName().v(f.getName());
                GenericFSEntry gen = fsDao.getGenericFileByName(genParentDummy);
                if (gen != null) {
                    fsEntry = gen.ins();
                }
            }
            // still finding.. might be root dir
            if (fsEntry == null && f.isDirectory()) {
                fsEntry = fsDao.getFsDirectoryByPath(f);
            }
            timerInternal1.stop();
            //file might been deleted yet :(
            if (!f.exists() && fsEntry == null)
                continue;
            // stage actual File
            stage = new Stage().setName(f.getName()).setIsDirectory(f.isDirectory());
            if (fsEntry != null) {
                stage.setFsId(fsEntry.getId().v()).setFsParentId(fsEntry.getParentId().v());
                //check for fastboot
                indexHelper.fastBoot(f, fsEntry, stage);
            }
            if (fsParent != null) {
                stage.setFsParentId(fsParent.getId().v());
            }
            // we found everything which already exists in das datenbank

            timer.start();
            Stage stageParent = indexHelper.connectToFs(parent);
            timer.stop();
            timerInternal2.start();
            if (stageParent != null)
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
            if (stage.getIsDirectory()) {
                indexWatchdogListener.watchDirectory(f);
            }
            stage.setOrder(order.ord());
            stageDao.insert(stage);
            timerInternal2.stop();

        }
        // done here. set the indexer to work

        timer.print();
        timerInternal1.print();
        timerInternal2.print();
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + serviceName;
    }

    private void roamDirectoryStage(Stage stage, AFile stageFile) throws SqlQueriesException, IOException, InterruptedException {
        if (stage.getIsDirectory() && stage.getDeleted())
            return;

        //todo debug
        if (stageFile.getName().equals("wrong"))
            Lok.debug("debug");

        FsBashDetails fsBashDetails = BashTools.getFsBashDetails(stageFile);
        // skip if drive ignores symlinks
        if (fsBashDetails.isSymLink() && !databaseManager.getDriveSettings().getUseSymLinks()) {
            return;
        }


        FsDirectory newFsDirectory = new FsDirectory();
        // roam directory if necessary
        AFile[] files = stageFile.listFiles();
        AFile[] subDirs = stageFile.listDirectories();


        // map will contain all FsEntry that must be deleted
        Map<String, GenericFSEntry> stuffToDelete = new HashMap<>();
        if (stage.getFsId() != null) {
            List<GenericFSEntry> generics = fsDao.getContentByFsDirectory(stage.getFsId());
            for (GenericFSEntry gen : generics) {
                if (gen.getSynced().v())
                    stuffToDelete.put(gen.getName().v(), gen);
            }
        }
        // remove deleted stuff first (because of the order)
        if (files != null) {
            for (AFile subFile : files) {
                stuffToDelete.remove(subFile.getName());
                // check if file is supposed to be here.
                // it just might not be transferred yet.
//                FsFile fsFile = fsDao.getFsFileByFile(subFile);
//                if (fsFile != null && fsFile.getSynced().v())
//                    stuffToDelete.remove(subFile.getName());
            }
        }
        if (subDirs != null) {
            for (AFile subDir : subDirs) {
                stuffToDelete.remove(subDir.getName());
            }
        }
        for (String name : stuffToDelete.keySet()) {
            GenericFSEntry gen = stuffToDelete.get(name);
            Stage alreadyOnStage = stageDao.getStageByFsId(gen.getId().v(), stageSetId);
            if (alreadyOnStage == null) {
                Stage delStage = GenericFSEntry.generic2Stage(gen, stageSetId)
                        .setDeleted(true)
                        .setOrder(order.ord())
                        .setVersion(gen.getVersion().v())
                        .setiNode(gen.getiNode().v())
                        .setModified(gen.getModified().v())
//                        .setSynced(gen.getSynced().v())
                        .setSynced(true)
                        .setParentId(stage.getId());
                stageDao.insert(delStage);
            } else {
                alreadyOnStage.setContentHash(gen.getContentHash().v());
                alreadyOnStage.setiNode(gen.getiNode().v());
                stage.setSize(gen.getSize().v());
                stage.setModified(gen.getModified().v());
                stageDao.update(alreadyOnStage);
            }
        }
        if (files != null) {
            // this speeds up things dramatically
            Map<String, Stage> contentMap = new HashMap<>();
            {
                List<Stage> content = stageDao.getStageContent(stage.getId());
                N.forEach(content, stage1 -> contentMap.put(stage1.getName(), stage1));
            }
            for (AFile subFile : files) {
                // check if which subFiles are on stage or fs. if not, index them

                Stage subStage = contentMap.get(subFile.getName());
                FsFile subFsFile = fsDao.getFileByName(stage.getFsId(), subFile.getName());
                FsBashDetails subFsBashDetails = BashTools.getFsBashDetails(subFile);
                if (subFsBashDetails.isSymLink() && !databaseManager.getDriveSettings().getUseSymLinks()) {
                    continue;
                }
                newFsDirectory.addFile(new FsFile(subFile));

                if (subStage == null && subFsFile == null) {
                    // stage
                    subStage = new Stage().setName(subFile.getName())
                            .setIsDirectory(false)
                            .setParentId(stage.getId())
                            .setFsParentId(stage.getFsId())
                            .setStageSet(stageSetId)
                            .setDeleted(false)
                            .setOrder(order.ord());
                    stageDao.insert(subStage);
                    this.updateFileStage(subStage, subFile, null, null, subFsBashDetails);
//                    subStage.setOrder(order.ord());
                }
            }
        }
        if (subDirs != null)
            for (AFile subDir : subDirs) {
                if (subDir.getAbsolutePath().equals(databaseManager.getDriveSettings().getTransferDirectory().getAbsolutePath()))
                    continue;
                FsBashDetails subDirDetails = BashTools.getFsBashDetails(subDir);
                stuffToDelete.remove(subDir.getName());

                if (subDirDetails.isSymLink() && !databaseManager.getDriveSettings().getUseSymLinks())
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
                            .setFsParentId(stage.getFsId())
                            .setName(subDir.getName())
                            .setIsDirectory(true)
                            .setDeleted(!subDir.exists());
                    Lok.debug("StageIndexerRunnable[" + stageSetId + "].roamDirectoryStage.roam sub: " + subDir.getAbsolutePath());
                    subStage.setOrder(order.ord());
                    try {
                        stageDao.insert(subStage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
//                    roamDirectoryStage(subStage, subDir);
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
        stage.setModified(fsBashDetails.getModified())
                .setiNode(fsBashDetails.getiNode())
                .setSymLink(fsBashDetails.isSymLink());
        if (stage.getFsId() != null) {
            FsDirectory oldFsDirectory = fsDao.getFsDirectoryById(stage.getFsId());
            if (oldFsDirectory.getContentHash().v().equals(newFsDirectory.getContentHash().v()))
                stageDao.markRemoved(stage.getId());
            else
                stageDao.update(stage);
        } else
            stageDao.update(stage);
        waitLock.unlockWrite();
        waitLock.lockWrite();
    }

    private void updateFileStage(Stage stage, AFile stageFile, OTimer timer1, OTimer timer2, FsBashDetails fsBashDetails) throws IOException, SqlQueriesException, InterruptedException {
        // skip hashing if information is complete & fastBoot is enabled-> speeds up booting
        if (stageFile.exists()) {

            if (timer1 != null)
                timer1.start();

            if (!fastBooting
                    || stage.getContentHashPair().isNull()
                    || stage.getiNodePair().isNull()
                    || stage.getModifiedPair().isNull()
                    || stage.getSizePair().isNull()) {

                if (fsBashDetails == null)
                    fsBashDetails = BashTools.getFsBashDetails(stageFile);
                if (fsBashDetails.isSymLink()) {
                    stage.setSymLink(true);
                    stage.setContentHash("0");
                } else {
                    stage.setSize(stageFile.length());
                    stage.setContentHash(Hash.md5(stageFile.inputStream()));
                }
                stage.setiNode(fsBashDetails.getiNode());
                stage.setModified(fsBashDetails.getModified());

            }

            if (timer1 != null)
                timer1.stop();
            if (timer2 != null)
                timer2.start();

            // stage can be deleted if nothing changed
            if (stage.getFsId() != null) {
                FsEntry fsEntry = fsDao.getFile(stage.getFsId());
                if (fsEntry.getContentHash().v().equals(stage.getContentHash()))
                    stageDao.deleteStageById(stage.getId());
                else {
                    if (stage.isSymLink() && !databaseManager.getDriveSettings().getUseSymLinks()) {
                        stageDao.deleteStageById(stage.getId());
                    } else {
                        stageDao.update(stage);
                    }
                }
            } else {
                if (stage.isSymLink() && !databaseManager.getDriveSettings().getUseSymLinks()) {
                    stageDao.deleteStageById(stage.getId());
                } else {
                    stageDao.update(stage);
                }
            }
            if (timer2 != null)
                timer2.stop();

        } else {
            stage.setDeleted(true);
            stageDao.update(stage);
        }
//        }
    }
}

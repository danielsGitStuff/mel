package de.mein.drive.index;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.Eva;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.core.serialize.serialize.tools.OTimer;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.ModifiedAndInode;
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
        //todo debug
        if (stage.getName().equals("testdir2"))
            Lok.debug("AbstractIndexer.buildPathFromStage.debug.1");
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
        //todo debug
        if (res.length() == 0)
            Lok.debug("AbstractIndexer.buildPathFromStage.debug.2");
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
        //todo problem
        OTimer timer = new OTimer("examine all");
        OTimer timer1 = new OTimer("examine 1");
        OTimer timer2 = new OTimer("examine 2");
        OTimer timer3 = new OTimer("examine 2");
        OTimer timerRoam1 = new OTimer("roamdDirectory.internal.1");
        OTimer timerRoam2 = new OTimer("roamdDirectory.internal.2");
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
                    roamDirectoryStage(stage, f, timerRoam1, timerRoam2);
                    timer2.stop();
                } else {
                    timer3.start();
                    this.updateFileStage(stage, f);
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
        timerRoam1.print();
        timerRoam2.print();
    }


    protected void initStage(String stageSetType, Iterator<AFile> iterator, IndexWatchdogListener indexWatchdogListener) throws IOException, SqlQueriesException {
        //todo debug
        OTimer timer = new OTimer("initStage().connect2fs");
        OTimer timerInternal1 = new OTimer("initStage.internal.1");
        OTimer timerInternal2 = new OTimer("initStage.internal.2");

//        IndexIterator iterator = new IndexIterator(it, databaseManager);

//        stageDao.lockWrite();
        stageSet = stageDao.createStageSet(stageSetType, null, null, null);
        final int rootPathLength = databaseManager.getDriveSettings().getRootDirectory().getPath().length();
        String path = "none yet";
        this.stageSetId = stageSet.getId().v();

        IndexHelper indexHelper = new IndexHelper(databaseManager, stageSetId, order);
        while (iterator.hasNext()) {
            AFile f = iterator.next();
            AFile parent = f.getParentFile();
            FsDirectory fsParent = null;
            FsEntry fsEntry = null;
            Stage stage;

            timerInternal1.start();

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
                fastBoot(f, fsEntry, stage);
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

    private void fastBoot(AFile file, FsEntry fsEntry, Stage stage) {
        if (fastBooting) {
            try {
                ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(file);
                if (fsEntry.getModified().equalsValue(modifiedAndInode.getModified())
                        && fsEntry.getiNode().equalsValue(modifiedAndInode.getiNode())
                        && ((fsEntry.getIsDirectory().v() && file.isDirectory()) || fsEntry.getSize().equalsValue(file.length()))) {
                    stage.setiNode(modifiedAndInode.getiNode());
                    stage.setModified(modifiedAndInode.getModified());
                    stage.setContentHash(fsEntry.getContentHash().v());
                    stage.setSize(fsEntry.getSize().v());
                    stage.setSynced(true);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + serviceName;
    }

    protected void roamDirectoryStage(Stage stage, AFile stageFile, OTimer timer1, OTimer timer2) throws SqlQueriesException, IOException, InterruptedException {
        if (stage.getIsDirectory() && stage.getDeleted())
            return;

        Lok.debug("AbstractIndexer.roamDirectoryStage: " + stageFile.getAbsolutePath());
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
        timer1.start();
        if (files != null) {
            // this speeds up things dramatically
            Map<String, Stage> contentMap = new HashMap<>();
            {
                List<Stage> content = stageDao.getStageContent(stage.getId());
                N.forEach(content, stage1 -> contentMap.put(stage1.getName(), stage1));
            }
            for (AFile subFile : files) {
                newFsDirectory.addFile(new FsFile(subFile));
                // check if which subFiles are on stage or fs. if not, index them

                Stage subStage = contentMap.get(subFile.getName());
                FsFile subFsFile = fsDao.getFileByName(stage.getFsId(), subFile.getName());
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
                    this.updateFileStage(subStage, subFile);
                    subStage.setOrder(order.ord());
                }
            }
        }
        timer1.stop();
        if (subDirs != null)
            for (AFile subDir : subDirs) {
                if (subDir.getAbsolutePath().equals(databaseManager.getDriveSettings().getTransferDirectory().getAbsolutePath()))
                    continue;
                stuffToDelete.remove(subDir.getName());
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
                    //todo debug
                    if (subStage.getDeleted() == true && subStage.getName().equals("samesub"))
                        Lok.debug("AbstractIndexer[" + stageSetId + "].roamDirectoryStage");
                    if (subStage.getDeleted() == null)
                        Lok.debug("AbstractIndexer[" + stageSetId + "].roamDirectoryStage.debugemsagß5");
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
        //todo debug
        if (stage.getName().equals("samedir"))
            Lok.debug("AbstractIndexer[" + stageSetId + "].roamDirectoryStage.h90984th030g5");
        RWLock waitLock = new RWLock().lockWrite();
        ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(stageFile);
        stage.setModified(modifiedAndInode.getModified())
                .setiNode(modifiedAndInode.getiNode());
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

    protected void updateFileStage(Stage stage, AFile stageFile) throws IOException, SqlQueriesException, InterruptedException {
        // skip hashing if information is complete & fastBoot is enabled-> speeds up booting
        if (stageFile.exists()) {
            //
            if (!fastBooting
                    || stage.getContentHashPair().isNull()
                    || stage.getiNodePair().isNull()
                    || stage.getModifiedPair().isNull()
                    || stage.getSizePair().isNull()) {
                ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(stageFile);
                stage.setContentHash(Hash.md5(stageFile.inputStream()));
                stage.setiNode(modifiedAndInode.getiNode());
                stage.setModified(modifiedAndInode.getModified());
                stage.setSize(stageFile.length());
            } else {
                // test evaluation
                Eva.flag("fast spawn 1");
            }
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
//        }
    }
}

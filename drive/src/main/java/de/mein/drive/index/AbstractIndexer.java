package de.mein.drive.index;

import de.mein.DeferredRunnable;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
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
        this.stageDao = databaseManager.getStageDao();
        this.fsDao = databaseManager.getFsDao();
        this.serviceName = databaseManager.getMeinDriveService().getRunnableName();
    }

    @Override
    public void onShutDown() {
        System.out.println(getClass().getSimpleName() + "[" + stageSetId + "] for " + serviceName + ".onShutDown");
    }

    protected String buildPathFromStage(Stage stage) throws SqlQueriesException {
        //todo debug
        if (stage.getName().equals("testdir2"))
            System.out.println("AbstractIndexer.buildPathFromStage.debug.1");
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
            System.out.println("AbstractIndexer.buildPathFromStage.debug.2");
        return res;
    }

    protected void examineStage() throws SqlQueriesException, IOException {
        //todo problem
        N.sqlResource(stageDao.getStagesByStageSet(stageSetId), stages -> {
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
                stage = stages.getNext();
            }
        });
        stageDao.deleteMarkedForRemoval(stageSet.getId().v());
        System.out.println("StageIndexerRunnable.runTry(" + stageSetId + ").finished");
        stageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(stageSet);
    }


    protected void initStage(String stageSetType, Iterator<String> iterator, IndexWatchdogListener indexWatchdogListener) throws IOException, SqlQueriesException {
//        stageDao.lockWrite();
        stageSet = stageDao.createStageSet(stageSetType, null, null, null);
        final int rootPathLength = databaseManager.getDriveSettings().getRootDirectory().getPath().length();
        String path = "none yet";
        this.stageSetId = stageSet.getId().v();
        while (iterator.hasNext()) {
            path = iterator.next();
//            System.out.println("AbstractIndexer[" + stageSetId + "].initStage.fromBashTools: " + path);
            File f = new File(path);
            File parent = f.getParentFile();
            FsDirectory fsParent = null;
            FsEntry fsEntry = null;
            Stage stage;
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
            // still finding.. might be root dir
            if (fsEntry == null && f.isDirectory()) {
                fsEntry = fsDao.getFsDirectoryByPath(f);
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
            Stage stageParent = stageDao.getStageByPath(stageSet.getId().v(), parent);
            stageParent = connectToFs(parent);
//            if (stageParent == null && parent.getAbsolutePath().length() >= rootPathLength) {
//                stageParent = new Stage().setStageSet(stageSet.getId().v());
//                if (fsParent == null) {
//                    //todo check if necessary
//                    stageParent = connectToFs(,stageParent, parent);
//                } else {
//                    stageParent.setName(fsParent.getName().v())
//                            .setFsId(fsParent.getId().v())
//                            .setFsParentId(fsParent.getParentId().v())
//                            .setStageSet(stageSet.getId().v())
//                            .setOldVersion(fsParent.getOldVersion().v())
//                            .setIsDirectory(fsParent.getIsDirectory().v());
//                    File exists = fsDao.getFileByFsFile(databaseManager.getDriveSettings().getRootDirectory(), fsParent);
//                    stageParent.setDeleted(!exists.exists());
//                }
//                stageParent.setOrder(order.ord());
//                stageParent.setSynced(true);
//                stageDao.insert(stageParent);
//            }
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
                stage.setSynced(true);
                indexWatchdogListener.watchDirectory(f);
            } else
                stage.setSynced(true);
            stage.setOrder(order.ord());
            stageDao.insert(stage);

        }
        // done here. set the indexer to work
    }

    private Stage connectToFs(File directory) throws SqlQueriesException {
        final int rootPathLength = databaseManager.getDriveSettings().getRootDirectory().getPath().length();
        if (directory.getAbsolutePath().length() < rootPathLength)
            return null;
        Stack<File> fileStack = new Stack<>();
        File parent = directory;
        while (parent.getAbsolutePath().length() > rootPathLength) {
            fileStack.push(parent);
            parent = parent.getParentFile();
        }
        FsEntry bottomFsEntry = fsDao.getBottomFsEntry(fileStack);
        File bottomFile = fsDao.getFileByFsFile(databaseManager.getDriveSettings().getRootDirectory(), bottomFsEntry);
        Stage bottomStage = new Stage();
        bottomStage.setName(bottomFsEntry.getName().v())
                .setFsParentId(bottomFsEntry.getParentId().v())
                .setFsId(bottomFsEntry.getId().v())
                .setIsDirectory(true)
                .setDeleted(!bottomFile.exists())
                .setStageSet(stageSetId)
                .setOrder(order.ord());
        Stage alreadyStaged = stageDao.getStageByFsId(bottomFsEntry.getId().v(), stageSetId);
        if (alreadyStaged == null) {
            // copy contenthash etc if the file had not been touch
            if (fastBooting) {
                try {
                    ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(bottomFile);
                    if (bottomFsEntry.getModified().equalsValue(modifiedAndInode.getModified())
                            && bottomFsEntry.getiNode().equalsValue(modifiedAndInode.getiNode())
                            && bottomFsEntry.getSize().equalsValue(bottomFile.length())) {
                        bottomStage.setiNode(modifiedAndInode.getiNode());
                        bottomStage.setModified(modifiedAndInode.getModified());
                        bottomStage.setContentHash(bottomFsEntry.getContentHash().v());
                        bottomStage.setSize(bottomFsEntry.getSize().v());
                        bottomStage.setSynced(true);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
            stageDao.insert(bottomStage);
        } else {
            bottomStage = alreadyStaged;
        }
        Stage oldeBottom = bottomStage;
        while (!fileStack.empty()) {
            File file = fileStack.pop();
            bottomStage = new Stage()
                    .setName(file.getName())
                    .setIsDirectory(true)
                    .setStageSet(stageSetId)
                    .setFsParentId(oldeBottom.getFsId())
                    .setParentId(oldeBottom.getId())
                    .setOrder(order.ord())
                    .setDeleted(!file.exists());
            alreadyStaged = stageDao.getStageByStageSetParentName(stageSetId, oldeBottom.getId(), file.getName());
            //stageDao.getStageByFsId(bottomFsEntry.getId().v(), stageSetId);
            if (alreadyStaged == null) {
                stageDao.insert(bottomStage);
                alreadyStaged = bottomStage;
                //oldeBottom = bottomStage;
            }
            oldeBottom = alreadyStaged;
        }
        if (alreadyStaged != null)
            return alreadyStaged;
        return bottomStage;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + serviceName;
    }

    protected void roamDirectoryStage(Stage stage, File stageFile) throws SqlQueriesException, IOException, InterruptedException {
        if (stage.getIsDirectory() && stage.getDeleted())
            return;
        //todo debug
        System.out.println("AbstractIndexer.roamDirectoryStage: " + stageFile.getAbsolutePath());
        FsDirectory newFsDirectory = new FsDirectory();
        // roam directory if necessary
        File[] files = stageFile.listFiles(File::isFile);
        File[] subDirs = stageFile.listFiles(File::isDirectory);
        if (files == null || subDirs == null)
            System.out.println("AbstractIndexer[" + stageSetId + "].roamDirectoryStage.dbuer903tj");
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
            for (File subFile : files) {
                stuffToDelete.remove(subFile.getName());
                // check if file is supposed to be here.
                // it just might not be transferred yet.
//                FsFile fsFile = fsDao.getFsFileByFile(subFile);
//                if (fsFile != null && fsFile.getSynced().v())
//                    stuffToDelete.remove(subFile.getName());
            }
        }
        if (subDirs != null) {
            for (File subDir : subDirs) {
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
                        .setSynced(gen.getSynced().v())
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
                            .setDeleted(false)
                            .setOrder(order.ord());
                    stageDao.insert(subStage);
                    //todo debug
                    if (subStage.getId() == null)
                        System.out.println("AbstractIndexer.roamDirectoryStage.debugong04");
                    this.updateFileStage(subStage, subFile);
                    subStage.setOrder(order.ord());
                }
            }
        }
        //todo debug
        if (subDirs == null)
            System.out.println("AbstractIndexer.roamDirectoryStage.debug.1");
        if (subDirs != null)
            for (File subDir : subDirs) {
                if (subDir.getAbsolutePath().equals(databaseManager.getDriveSettings().getTransferDirectoryPath()))
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
                            .setDeleted(!subDir.exists())
                            .setSynced(true);
                    System.out.println("StageIndexerRunnable[" + stageSetId + "].roamDirectoryStage.roam sub: " + subDir.getAbsolutePath());
                    subStage.setOrder(order.ord());
                    //todo debug
                    if (subStage.getDeleted() == true && subStage.getName().equals("samesub"))
                        System.out.println("AbstractIndexer[" + stageSetId + "].roamDirectoryStage");
                    if (subStage.getDeleted() == null)
                        System.out.println("AbstractIndexer[" + stageSetId + "].roamDirectoryStage.debugemsagß5");
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
        //todo debug
        if (stage.getName().equals("samedir"))
            System.out.println("AbstractIndexer[" + stageSetId + "].roamDirectoryStage.h90984th030g5");
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

    protected void updateFileStage(Stage stage, File stageFile) throws IOException, SqlQueriesException, InterruptedException {
        // skip hashing if information is complete -> speeds up booting
        if (stageFile.exists() && (stage.getModifiedPair().isNull() || stage.getiNodePair().isNull() || stage.getContentHashPair().isNull())) {
            ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(stageFile);
            stage.setContentHash(Hash.md5(stageFile));
            stage.setiNode(modifiedAndInode.getiNode());
            stage.setModified(modifiedAndInode.getModified());
            stage.setSize(stageFile.length());
            stage.setSynced(true);
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
}

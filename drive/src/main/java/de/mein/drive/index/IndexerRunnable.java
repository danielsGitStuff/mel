package de.mein.drive.index;

import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.SyncHandler;
import de.mein.drive.sql.*;
import de.mein.drive.watchdog.IndexWatchdogListener;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 10.07.2016.
 */
public class IndexerRunnable implements Runnable {

    private final DriveDatabaseManager databaseManager;
    private final IndexWatchdogListener indexWatchdogListener;
    private SyncHandler syncHandler;
    private List<ICrawlerListener> listeners = new ArrayList<>();
    private final FileFilter directoryFileFilter = pathname -> pathname.isDirectory();
    private IndexPersistence indexPersistence;
    private RootDirectory rootDirectory;
    private String transferDirectoryPath;

    /**
     * the @IndexWatchdogListener is somewhat special. we need it elsewhere
     *
     * @param databaseManager
     * @param indexWatchdogListener
     * @param listeners
     * @throws SqlQueriesException
     */
    public IndexerRunnable(DriveDatabaseManager databaseManager, IndexWatchdogListener indexWatchdogListener, ICrawlerListener... listeners) throws SqlQueriesException {
        this.listeners.add(indexWatchdogListener);
        for (ICrawlerListener listener : listeners)
            this.listeners.add(listener);
        this.databaseManager = databaseManager;
        this.transferDirectoryPath = databaseManager.getDriveSettings().getTransferDirectoryPath();
        this.indexWatchdogListener = indexWatchdogListener;
        this.indexPersistence = new IndexPersistence(databaseManager);//.createRootDirectory(rootFile, "test name");
        this.rootDirectory = databaseManager.getDriveSettings().getRootDirectory();
    }

    public IndexerRunnable setSyncHandler(SyncHandler syncHandler) {
        this.syncHandler = syncHandler;
        return this;
    }

    public IndexWatchdogListener getIndexWatchdogListener() {
        return indexWatchdogListener;
    }

    @Override
    public void run() {
        try {
            System.out.println("IndexerRunnable.runTry.roaming");
            // if root directory does not exist: create one
            FsDirectory fsRoot; //= databaseManager.getFsDao().getDirectoryById(rootDirectory.getId());
            if (rootDirectory.getId() == null) {
                fsRoot = (FsDirectory) new FsDirectory().setName("[root]").setVersion(0L);
                fsRoot.setOriginalFile(new File(rootDirectory.getPath()));
                fsRoot = (FsDirectory) databaseManager.getFsDao().insert(fsRoot);
                databaseManager.getDriveSettings().getRootDirectory().setId(fsRoot.getId().v());
            } else {
                fsRoot = databaseManager.getFsDao().getDirectoryById(rootDirectory.getId());
                if (fsRoot.getOriginal() == null) {
                    fsRoot.setOriginalFile(new File(rootDirectory.getPath()));
                }
            }
            // we will stage changes, so we need a StageSet
            StageSet stageSet = databaseManager.getStageDao().createStageSet("startup index", null, null);
            roamDirectory(null, null, fsRoot, stageSet.getId().v());
            System.out.println("IndexerRunnable.runTry.save in mem db");
            databaseManager.updateVersion();
            for (ICrawlerListener listener : listeners)
                listener.done();
            if (databaseManager.getMeinDriveService() instanceof MeinDriveServerService)
                syncHandler.commitStage(stageSet.getId().v());
            System.out.println("IndexerRunnable.runTry.done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void roamDirectory(FsDirectory parent, Stage parentStage, FsDirectory actualDirectory, long stageSetId) throws IOException {
        try {
            FsDirectory dbDirectory = databaseManager.getFsDao().getDirectoryById(actualDirectory.getId().v()); //indexPersistence.roamDirectory(parent, actualDirectory, stageSetId);
            // in case of actualDirectory being the RootDirectory, both instances will be the same. There is only one floating around
            Stage stage = null;
            for (ICrawlerListener listener : listeners)
                listener.foundDirectory(actualDirectory);
            // get files
            File[] fileList = actualDirectory.listFiles(File::isFile);
            File[] subDirectories = actualDirectory.listFiles(f -> !f.isFile());
            // add all Files
            for (File f : fileList) {
                FsFile fs = new FsFile(f);
                actualDirectory.addFile(fs);
            }
            //add all subDirectories
            for (File d : subDirectories) {
                if (!d.getAbsolutePath().equals(databaseManager.getDriveSettings().getTransferDirectoryPath())) {
                    FsDirectory subDir = new FsDirectory(d);
                    actualDirectory.addSubDirectory(subDir);
                }
            }
            BashTools.NodeAndTime nodeAndTime = BashTools.getNodeAndTime(actualDirectory.getOriginal());

            //check hash
            actualDirectory.calcContentHash();
            if (dbDirectory == null || (dbDirectory != null && !dbDirectory.getContentHash().v().equals(actualDirectory.getContentHash().v()))) {
                System.out.println("IndexerRunnable.roamDirectory.gotta stage");
                stage = new Stage()
                        .setIsDirectory(true)
                        .setName(actualDirectory.getName().v())
                        .setStageSet(stageSetId)
                        .setContentHash(actualDirectory.getContentHash().v())
                        .setiNode(nodeAndTime.getInode())
                        .setModified(nodeAndTime.getModifiedTime())
                        .setDeleted(false)
                        .setSynced(true);
                if (dbDirectory != null) {
                    stage.setFsId(dbDirectory.getId().v())
                            .setFsParentId(dbDirectory.getParentId().v());
                }
                if (parentStage != null) {
                    stage.setParentId(parentStage.getId());
                }
                databaseManager.getStageDao().insert(stage);
            }
            //check files
            for (FsFile fs : actualDirectory.getFiles()) {
                Long parentId = null;
                if (parent != null)
                    parentId = parent.getId().v();
                FsFile dbFile = databaseManager.getFsDao().getFileByName(parentId, fs.getName().v());
                String md5 = de.mein.core.Hash.md5(fs.getOriginal());
                if (dbFile == null || (dbFile != null && !dbFile.getContentHash().v().equals(md5))) {
                    BashTools.NodeAndTime fNodeTime = BashTools.getNodeAndTime(fs.getOriginal());
                    Stage fStage = new Stage()
                            .setName(fs.getName().v())
                            .setIsDirectory(false)
                            .setContentHash(md5)
                            .setStageSet(stageSetId)
                            .setSize(fs.getOriginal().length())
                            .setiNode(fNodeTime.getInode())
                            .setModified(fNodeTime.getModifiedTime())
                            .setDeleted(false)
                            .setSynced(true);
                    if (dbFile != null)
                        fStage.setFsParentId(dbFile.getParentId().v())
                                .setFsId(dbFile.getId().v());
                    if (stage != null)
                        fStage.setParentId(stage.getId());
                    databaseManager.getStageDao().insert(fStage);
                }
            }
            //roam
            for (FsDirectory subDir : actualDirectory.getSubDirectories()) {
                roamDirectory(actualDirectory, stage, subDir, stageSetId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public RootDirectory getRootDirectory() {
        return rootDirectory;
    }
}

package de.mel.filesync.service.sync;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.filesync.index.Indexer;
import de.mel.filesync.nio.FileDistributionTask;
import de.mel.filesync.nio.FileDistributor;
import de.mel.filesync.quota.OutOfSpaceException;
import de.mel.filesync.quota.QuotaManager;
import de.mel.filesync.service.MelFileSyncService;
import de.mel.filesync.service.Wastebin;
import de.mel.filesync.sql.*;
import de.mel.filesync.sql.dao.*;
import de.mel.filesync.transfer.TManager;
import de.mel.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Stack;


/**
 * Created by xor on 1/26/17.
 */
@SuppressWarnings("ALL")
public abstract class SyncHandler {
    protected final FileSyncSettings fileSyncSettings;
    protected final MelFileSyncService melFileSyncService;
    protected final TManager transferManager;
    protected final MelAuthService melAuthService;
    private final FileDistributor fileDistributor;
    private final FileDistTaskDao fileDistTaskDao;
    private final FsWriteDao fsWriteDao;
    protected FsDao fsDao;
    protected StageDao stageDao;
    protected N runner = new N(Throwable::printStackTrace);
    protected FileSyncDatabaseManager fileSyncDatabaseManager;
    protected Indexer indexer;
    protected Wastebin wastebin;
    protected QuotaManager quotaManager;

    public FsDao getFsDao() {
        return fsDao;
    }

    public TransferDao getTransferDao() {
        return melFileSyncService.getFileSyncDatabaseManager().getTransferDao();
    }

    public SyncHandler(MelAuthService melAuthService, MelFileSyncService melFileSyncService) {
        this.melAuthService = melAuthService;
        this.fsDao = melFileSyncService.getFileSyncDatabaseManager().getFsDao();
        this.stageDao = melFileSyncService.getFileSyncDatabaseManager().getStageDao();
        this.fileDistTaskDao = melFileSyncService.getFileSyncDatabaseManager().getFileDistTaskDao();
        this.fileSyncSettings = melFileSyncService.getFileSyncSettings();
        this.melFileSyncService = melFileSyncService;
        this.fileSyncDatabaseManager = melFileSyncService.getFileSyncDatabaseManager();
        this.fsWriteDao = fileSyncDatabaseManager.getFsWriteDao();
        this.indexer = melFileSyncService.getIndexer();
        this.wastebin = melFileSyncService.getWastebin();
        this.transferManager = new TManager(melAuthService, melFileSyncService.getFileSyncDatabaseManager().getTransferDao(), melFileSyncService, this, wastebin, fsDao);
//        this.transferManager = new TransferManager(melAuthService, melDriveService, melDriveService.getDriveDatabaseManager().getTransferDao()
//                , wastebin, this);
        this.quotaManager = new QuotaManager(melFileSyncService);
        this.fileDistributor = FileDistributor.Companion.getFactory().createInstance(melFileSyncService);
    }

    public FileDistTaskDao getFileDistTaskDao() {
        return fileDistTaskDao;
    }

    /**
     * call this if you are the receiver
     */
    public void researchTransfers() {
        transferManager.research();
    }

    public FileDistributor getFileDistributor() {
        return fileDistributor;
    }

    //    public AFile moveFile(AFile source, FsFile fsTarget) throws SqlQueriesException, IOException {
//        AFile target = null;
//        try {
//            //fsDao.lockWrite();
//            target = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsTarget);
//            Lok.debug("SyncHandler.moveFile (" + source.getAbsolutePath() + ") -> (" + target.getAbsolutePath() + ")");
//            // check if there already is a file & delete
//            if (target.exists()) {
//                FsBashDetails fsBashDetails = BashTools.Companion.getFsBashDetails(target);
//                // file had to be marked as deleted before, which means the inode and so on appear in the wastebin
//                Waste waste = melDriveService.getDriveDatabaseManager().getWasteDao().getWasteByInode(fsBashDetails.getiNode());
//                GenericFSEntry genericFSEntry = fsDao.getGenericByINode(fsBashDetails.getiNode());
//                if (target.isFile()) {
//                    if (waste != null) {
//                        if (waste.getModified().v().equals(fsBashDetails.getModified())) {
//                            wastebin.moveToBin(waste, target);
//                        } else {
//                            System.err.println("SyncHandler.moveFile: File was modified in the meantime :(");
//                            System.err.println("SyncHandler.moveFile: " + target.getAbsolutePath());
//                        }
//                    } else if ((fsTarget.getModified().isNull() || (fsTarget.getModified().notNull() && !fsTarget.getModified().v().equals(fsBashDetails.getModified())))
//                            || (fsTarget.getiNode().isNull() || fsTarget.getiNode().notNull() && !fsTarget.getiNode().v().equals(fsBashDetails.getiNode()))) {
//                        //file is not equal to the one in the fs table
//                        wastebin.deleteUnknown(target);
//                    } else {
//                        System.err.println(getClass().getSimpleName() + ".moveFile().errrr.files.identical? .. deleting anyway");
//                        wastebin.deleteUnknown(target);
//                    }
//                }
//            }
//            indexer.ignorePath(target.getAbsolutePath(), 1);
//            FsBashDetails fsBashDetails = BashTools.Companion.getFsBashDetails(source);
//            fsTarget.getiNode().v(fsBashDetails.getiNode());
//            fsTarget.getModified().v(fsBashDetails.getModified());
//            fsTarget.getSize().v(source.length());
//            fsTarget.getSynced().v(true);
//            boolean moved = source.move(target);
//            if (!moved || !target.exists())
//                return null;
//            fsDao.update(fsTarget);
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            //fsDao.unlockWrite();
//        }
//        return target;
//    }

    public void suspend() {
        if (transferManager != null)
            this.transferManager.stop();
        if (fileDistributor != null)
            fileDistributor.stop();
    }

    public void onFileTransferFailed(String hash) {
        Warden warden = P.confine(P.read(fsDao));
        try {
            if (fsDao.desiresHash(hash)) {
                System.err.println(getClass().getSimpleName() + ".onFileTransferFailed() file with hash " + hash + " is required but failed to transfer");
            }
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        } finally {
            warden.end();
        }
    }

    /**
     * @param file   file in working directory
     * @param hash
     * @param warden
     * @return true if the file is new on the device (not a copy). so it can be transferred to other devices.
     * @throws SqlQueriesException
     */
    public boolean onFileTransferred(IFile file, String hash, Warden warden, FsFile sourceFsFile) throws SqlQueriesException, IOException {
        try {

            List<FsFile> fsFiles = fsDao.getNonSyncedFilesByHash(hash);

            FileDistributionTask distributionTask = N.result(() -> {
                FileDistributionTask task = fileDistTaskDao.getNotReadyYetByHash(hash);
                if (task != null)
                    return task;
                return new FileDistributionTask();
            });
            distributionTask.setSourceFile(file);
            distributionTask.setDeleteSource(true);
            distributionTask.setSourceHash(hash);

            // file found in transfer dir
            if (file.getAbsolutePath().startsWith(fileSyncDatabaseManager.getFileSyncSettings().getTransferDirectory().getAbsolutePath())) {
                // assuming that noone moves or copies files in this directory at runtime. some day someone will do it any, things will break and he will complain.
                FsBashDetails bashDetails = BashTools.Companion.getFsBashDetails(file);
                distributionTask.setOptionals(bashDetails, file.length());
                distributionTask.setDeleteSource(true);
                if (fsFiles.isEmpty())
                    return false;
                N.forEach(fsFiles, fsFile -> distributionTask.addTargetFile(fsDao.getFileByFsFile(fileSyncSettings.getRootDirectory(), fsFile), fsFile.getId().v()));
            } else {
                // this is in CASE: file found in FS-Directory...
                // and in this case sourceFsFile must not be null.
                // set what the copy service is expected to find as a source file.
                // in case it has changed it can abort
                FsBashDetails bashDetails = new FsBashDetails(sourceFsFile.getCreated().v(), sourceFsFile.getModified().v(), sourceFsFile.getiNode().v(), sourceFsFile.isSymlink(), null, sourceFsFile.getName().v()); //BashTools.Companion.getFsBashDetails(file);
                distributionTask.setOptionals(bashDetails, file.length());
                distributionTask.setDeleteSource(false);
                N.forEach(fsFiles, fsFile -> {
                    if (fsFile.getId().notEqualsValue(sourceFsFile.getId().v()))
                        distributionTask.addTargetFile(fsDao.getFileByFsFile(fileSyncSettings.getRootDirectory(), fsFile), fsFile.getId().v());
                });
            }

            // this might happen if there is still a transfer that has not been flagged properly. just skip here
            if (distributionTask.getTargetPaths().isEmpty())
                return false;
            distributionTask.setState(FileDistributionTask.FileDistributionState.READY);
            fileDistributor.completeJob(distributionTask);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return false;
    }


    public void commitStage(Long stageSetId, Warden warden) throws OutOfSpaceException {
        commitStage(stageSetId, warden, null);
    }


    /**
     * @param stageSetId
     */
    public void commitStage(Long stageSetId, Warden warden, Map<Long, Long> stageIdFsIdMap) throws OutOfSpaceException {
        /**
         * remember: files that come from fs are always synced. otherwise they might be synced (when merged) or are not synced (from remote)
         */

        warden.run(() -> {
            try {
                // sop files being moved around
                fileDistributor.stop();
                fsWriteDao.prepare();
                StageSet stageSet = stageDao.getStageSetById(stageSetId);
                // if version not provided by the stageset we will increase the old one
                Long localVersion = stageSet.getVersion().notNull() ? stageSet.getVersion().v() : fileSyncDatabaseManager.getFileSyncSettings().getLastSyncedVersion() + 1;
                //check if sufficient space is available
                if (!stageSet.fromFs())
                    quotaManager.freeSpaceForStageSet(stageSetId);

                // delete all files
                N.readSqlResourceIgnorantly(stageDao.getDeletedFileStagesByStageSet(stageSetId), (stages, stage) -> {
                    if (stage.getFsIdPair().notNull()) {
                        wastebin.deleteFsEntry(stage.getFsId());
                    } else {
                        Lok.error("DEBUG!!!");
                        Lok.error("DEBUG!!!");
                        Lok.error("DEBUG!!!");
                    }
                });

                // delete all folders
                N.readSqlResourceIgnorantly(stageDao.getDeletedDirectoryStagesByStageSet(stageSetId), (sqlResource, dirStage) -> {
                    if (dirStage.getFsIdPair().notNull())
                        fsWriteDao.deleteById(dirStage.getFsId());
                    IFile f = stageDao.getFileByStage(dirStage);
                    wastebin.deleteUnknown(f);
                });

                // put new stuff in place
                N.escalatingSqlResource(stageDao.getNotDeletedStagesByStageSet(stageSetId), stages -> {
                    Stage stage = stages.getNext();
                    while (stage != null) {
                        if (stage.getFsId() == null) {
                            if (stage.getIsDirectory()) {
                                FsDirectory dir = new FsDirectory();
                                dir.getVersion().v(localVersion);
                                dir.getContentHash().v(stage.getContentHash());
                                dir.getName().v(stage.getName());
                                dir.getModified().v(stage.getModified());
                                dir.getCreated().v(stage.getCreated());
                                dir.getiNode().v(stage.getiNode());
                                dir.getSymLink().v(stage.getSymLink());
                                dir.getDepth().v(stage.getDepth());
                                Long fsParentId = null;
                                if (stage.getParentId() != null) {
                                    fsParentId = stageDao.getStageById(stage.getParentId()).getFsId();
                                } else if (stage.getFsParentId() != null)
                                    fsParentId = stage.getFsParentId();
                                dir.getParentId().v(fsParentId);
                                appendPath(dir, stage);
                                fsWriteDao.insert(dir.toFsWriteEntry());
                                if (stageIdFsIdMap != null) {
                                    stageIdFsIdMap.put(stage.getId(), dir.getId().v());
                                }

                                this.createDirs(fileSyncDatabaseManager.getFileSyncSettings().getRootDirectory(), dir);

                                stage.setFsId(dir.getId().v());
                            } else {
                                // it is a new file
                                FsFile fsFile = null;
                                if (stage.getFsId() != null)
                                    fsFile = fsWriteDao.getFile(stage.getFsId());
                                else {
                                    fsFile = new FsFile();
                                    Long fsParentId = null;
                                    if (stage.getParentId() != null) {
                                        fsParentId = stageDao.getStageById(stage.getParentId()).getFsId();
                                    } else if (stage.getFsParentId() != null)
                                        fsParentId = stage.getFsParentId();
                                    fsFile.getParentId().v(fsParentId);
                                }
                                fsFile.getName().v(stage.getName());
                                fsFile.getContentHash().v(stage.getContentHash());
                                fsFile.getVersion().v(localVersion);
                                fsFile.getModified().v(stage.getModified());
                                fsFile.getCreated().v(stage.getCreated());
                                fsFile.getiNode().v(stage.getiNode());
                                fsFile.getSize().v(stage.getSize());
                                fsFile.getSymLink().v(stage.getSymLink());
                                fsFile.getDepth().v(stage.getDepth());
                                if (stageSet.fromFs()) {
                                    fsFile.getSynced().v(true);
                                } else {
                                    fsFile.getSynced().v(false);
                                }
                                appendPath(fsFile, stage);
                                fsWriteDao.insert(fsFile.toFsWriteEntry());
                                if (fsFile.isSymlink()) {
                                    IFile f = fsWriteDao.getFileByFsFile(fileSyncSettings.getRootDirectory(), fsFile);
                                    BashTools.Companion.lnS(f, fsFile.getSymLink().v());
                                } else if (!stageSet.fromFs() && !stage.getIsDirectory() && !stage.isSymLink()) {
                                    // this file porobably has to be transferred
                                    DbTransferDetails details = new DbTransferDetails();
                                    details.getAvailable().v(stage.getSynced());
                                    details.getCertId().v(stageSet.getOriginCertId());
                                    details.getServiceUuid().v(stageSet.getOriginServiceUuid());
                                    details.getHash().v(stage.getContentHash());
                                    details.getDeleted().v(false);
                                    details.getSize().v(stage.getSize());
                                    setupTransferAvailable(details, stageSet, stage);
                                    N.r(() -> transferManager.createTransfer(details));
                                }
                                if (stageIdFsIdMap != null) {
                                    stageIdFsIdMap.put(stage.getId(), fsFile.getId().v());
                                }
                                stage.setFsId(fsFile.getId().v());
                            }
                        } else { // fs.id is not null
                            if (stage.getFsParentId() != null && !fsWriteDao.hasId(stage.getFsParentId())) {//skip if parent was deleted
                                stage = stages.getNext();
                                continue;
                            }
                            if ((stage.getDeleted() != null && stage.getDeleted() && stage.getSynced() != null && stage.getSynced()) || (stage.getIsDirectory() && stage.getDeleted())) {
                                //if (stage.getDeleted() != null && stage.getSynced() != null && (stage.getDeleted() && stage.getSynced())) {
                                //todo BUG: 3 Conflict solve dialoge kommen hoch, wenn hier Haltepunkt bei DriveFXTest.complectConflict() drin ist
//                            wastebin.deleteFsEntry(stage.getFsId());
                                Lok.debug("debug");
                            } else {
                                FsEntry fsEntry = stageDao.stage2FsEntry(stage);
                                if (fsEntry.getVersion().isNull()) {
                                    Lok.debug("//pe, should not be called");
                                    fsEntry.getVersion().v(localVersion);
                                }
                                FsEntry oldeEntry = fsWriteDao.getGenericById(fsEntry.getId().v());
                                // only copy modified & inode if it is not present in the new entry (it came from remote then)
                                if (oldeEntry != null && oldeEntry.getIsDirectory().v() && fsEntry.getIsDirectory().v() && fsEntry.getModified().isNull()) {
                                    fsEntry.getiNode().v(oldeEntry.getiNode());
                                    fsEntry.getModified().v(oldeEntry.getModified());
                                    fsEntry.getCreated().v(oldeEntry.getCreated());
                                }
                                if (fsEntry.getId().v() != null && !fsEntry.getIsDirectory().v()) {
                                    FsFile oldeFsFile = fsWriteDao.getFile(fsEntry.getId().v());
                                    if (oldeFsFile != null && !stageSet.fromFs() && fsEntry.getSynced().notNull() && !fsEntry.getSynced().v()) {
                                        wastebin.deleteFsFile(oldeFsFile);
                                    } else {
                                        // delete file. consider that it might be in the same state as the stage
                                        IFile stageFile = stageDao.getFileByStage(stage);
                                        if (stageFile.exists()) {
                                            FsBashDetails fsBashDetails = BashTools.Companion.getFsBashDetails(stageFile);
                                            if (stage.getiNode() == null || stage.getModified() == null ||
                                                    !(fsBashDetails.getiNode().equals(stage.getiNode()) && fsBashDetails.getModified().equals(stage.getModified()))) {
                                                wastebin.deleteUnknown(stageFile);
//                                            stage.setSynced(false);
                                                // we could search more recent stagesets to find some clues here and prevent deleteUnknown().
                                            }
                                            // else: the file is as we want it to be
                                        }
                                    }
                                }
                                if (!fsEntry.getIsDirectory().v() && (stage.getSynced() != null && !stage.getSynced()))
                                    fsEntry.getSynced().v(false);
                                // its remote -> not in place
                                if (!stage.getIsDirectory() && !stageSet.fromFs())
                                    fsEntry.getSynced().v(false);
                                else if (stageSet.fromFs()) {
                                    fsEntry.getSynced().v(true);
                                }
                                fsWriteDao.insertOrUpdate(fsEntry.toFsWriteEntry());
                                if (stageSet.getOriginCertId().notNull() && !stage.getIsDirectory() && !stage.isSymLink()) {
                                    DbTransferDetails details = new DbTransferDetails();
                                    details.getCertId().v(stageSet.getOriginCertId());
                                    details.getServiceUuid().v(stageSet.getOriginServiceUuid());
                                    details.getHash().v(stage.getContentHash());
                                    details.getDeleted().v(false);
                                    details.getSize().v(stage.getSize());
                                    setupTransferAvailable(details, stageSet, stage);
                                    N.r(() -> transferManager.createTransfer(details));
                                }
                                this.createDirs(fileSyncDatabaseManager.getFileSyncSettings().getRootDirectory(), fsEntry);
                            }
                        }
                        stageDao.update(stage);
                        stage = stages.getNext();
                    }
                    fsWriteDao.commit();
                    fileSyncDatabaseManager.updateVersion();
                    stageDao.deleteStageSet(stageSetId);
                    transferManager.stop();
                    transferManager.removeUnnecessaryTransfers();
                    transferManager.start();
                    transferManager.research();
                });
                wastebin.maintenance();
            } catch (Exception e) {
                N.r(() -> fsWriteDao.cleanUp());
                throw e;
            }
        });
    }

    protected void setupTransferAvailable(DbTransferDetails details, StageSet stageSet, Stage stage) {
        if (!stage.getIsDirectory() && stage.getStageSetPair().notNull()) {
            details.getAvailable().v(stage.getSynced());
        }
    }

    /**
     * USES FSWRITE
     *
     * @param fsEntry
     * @param stage
     */
    private void appendPath(FsEntry fsEntry, Stage stage) throws SqlQueriesException {
        String path = "";
        if (fsEntry.getParentId().notNull()) {
            FsEntry parent = fsWriteDao.getGenericById(fsEntry.getParentId().v());
            path = parent.getPath().v() + parent.getName().v() + File.separator;
        } else if (stage != null) {
            Lok.debug("Error1");
        } else {
            Lok.debug("Error2");
        }
        fsEntry.setPath(path);
    }


    protected void createDirs(RootDirectory rootDirectory, FsEntry fsEntry) throws SqlQueriesException, IOException, InterruptedException {
        // todo debug
        if (fsEntry.getName().equalsValue("i0"))
            Lok.debug();
        // if synced, it came from fs and can be ignored
        if (fsEntry.getSynced().v())
            return;
        // assume that root directory already exists
        if (fsEntry.getParentId().v() == null)
            return;
        Stack<FsDirectory> stack = new Stack<>();
        FsDirectory dbParent = fsWriteDao.getDirectoryById(fsEntry.getParentId().v());
        while (dbParent != null && dbParent.getParentId().v() != null) {
            stack.add(dbParent);
            dbParent = fsWriteDao.getDirectoryById(dbParent.getParentId().v());
        }
        String path = rootDirectory.getPath() + File.separator;
        if (!stack.empty()) {
            while (!stack.empty()) {
                dbParent = stack.pop();
                path += dbParent.getName().v();
                IFile d = AbstractFile.instance(path);
                if (!d.exists()) {
                    indexer.ignorePath(path, 1);
                    Lok.debug("SyncHandler.createDirs: " + d.getAbsolutePath());
                    d.mkdirs();
                    indexer.watchDirectory(d);
                    updateInodeModified(dbParent, d);
                }
                path += File.separator;
            }
        } else {

        }
        if (fsEntry.getIsDirectory().v()) {
            path += fsEntry.getName().v();
            IFile target = AbstractFile.instance(path);
            if (fsEntry.isSymlink()) {
                if (!target.exists()) {
                    BashTools.Companion.lnS(target, fsEntry.getSymLink().v());
                }
            } else if (!target.exists()) {
                indexer.ignorePath(path, 1);
                Lok.debug("SyncHandler.createDirs: " + target.getAbsolutePath());
                target.mkdirs();
                indexer.watchDirectory(target);
                updateInodeModified(fsEntry, target);
            }
        }
    }

    private void updateInodeModified(FsEntry entry, IFile f) throws SqlQueriesException, IOException, InterruptedException {
        FsBashDetails fsBashDetails = BashTools.Companion.getFsBashDetails(f);
        entry.getiNode().v(fsBashDetails.getiNode());
        entry.getModified().v(fsBashDetails.getModified());
        entry.getCreated().v(fsBashDetails.getCreated());
        fsDao.update(entry);
    }

    public void start() {
        N.r(() -> wastebin.maintenance());
        transferManager.start();
    }


    public void resume() {
        if (transferManager != null)
            this.transferManager.resume();
    }

    public void onShutDown() {
        transferManager.onShutDown();
    }

    public boolean onFileTransferred(IFile file, String hash, Warden warden) throws IOException, SqlQueriesException {
        return this.onFileTransferred(file, hash, warden, null);
    }
}

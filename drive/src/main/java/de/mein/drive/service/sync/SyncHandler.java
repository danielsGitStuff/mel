package de.mein.drive.service.sync;

import de.mein.Lok;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.ModifiedAndInode;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.index.Indexer;
import de.mein.auth.file.AFile;
import de.mein.drive.quota.OutOfSpaceException;
import de.mein.drive.quota.QuotaManager;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.Wastebin;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.transfer.TransferManager;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Stack;


/**
 * Created by xor on 1/26/17.
 */
@SuppressWarnings("ALL")
public abstract class SyncHandler {
    protected final DriveSettings driveSettings;
    protected final MeinDriveService meinDriveService;
    protected final TransferManager transferManager;
    protected final MeinAuthService meinAuthService;
    protected FsDao fsDao;
    protected StageDao stageDao;
    protected N runner = new N(Throwable::printStackTrace);
    protected DriveDatabaseManager driveDatabaseManager;
    protected Indexer indexer;
    protected Wastebin wastebin;
    protected QuotaManager quotaManager;


    public SyncHandler(MeinAuthService meinAuthService, MeinDriveService meinDriveService) {
        this.meinAuthService = meinAuthService;
        this.fsDao = meinDriveService.getDriveDatabaseManager().getFsDao();
        this.stageDao = meinDriveService.getDriveDatabaseManager().getStageDao();
        this.driveSettings = meinDriveService.getDriveSettings();
        this.meinDriveService = meinDriveService;
        this.driveDatabaseManager = meinDriveService.getDriveDatabaseManager();
        this.indexer = meinDriveService.getIndexer();
        this.wastebin = meinDriveService.getWastebin();
        this.transferManager = new TransferManager(meinAuthService, meinDriveService, meinDriveService.getDriveDatabaseManager().getTransferDao()
                , wastebin, this);
        this.quotaManager = new QuotaManager(meinDriveService);
    }

    public AFile moveFile(AFile source, FsFile fsTarget) throws SqlQueriesException, IOException {
        AFile target = null;
        try {
            //fsDao.lockWrite();
            target = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsTarget);
            //todo debug
            if (target.getName().equals("sub2.txt"))
                Lok.debug("SyncHandler.moveFile.debugr23hr03w");
            Lok.debug("SyncHandler.moveFile (" + source.getAbsolutePath() + ") -> (" + target.getAbsolutePath() + ")");
            // check if there already is a file & delete
            if (target.exists()) {
                ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(target);
                // file had to be marked as deleted before, which means the inode and so on appear in the wastebin
                Waste waste = meinDriveService.getDriveDatabaseManager().getWasteDao().getWasteByInode(modifiedAndInode.getiNode());
                GenericFSEntry genericFSEntry = fsDao.getGenericByINode(modifiedAndInode.getiNode());
                if (target.isFile()) {
                    if (waste != null) {
                        if (waste.getModified().v().equals(modifiedAndInode.getModified())) {
                            wastebin.moveToBin(waste, target);
                        } else {
                            System.err.println("SyncHandler.moveFile: File was modified in the meantime :(");
                            System.err.println("SyncHandler.moveFile: " + target.getAbsolutePath());
                        }
                    } else if ((fsTarget.getModified().isNull() || (fsTarget.getModified().notNull() && !fsTarget.getModified().v().equals(modifiedAndInode.getModified())))
                            || (fsTarget.getiNode().isNull() || fsTarget.getiNode().notNull() && !fsTarget.getiNode().v().equals(modifiedAndInode.getiNode()))) {
                        //file is not equal to the one in the fs table
                        wastebin.deleteUnknown(target);
                    } else {
                        System.err.println(getClass().getSimpleName() + ".moveFile().errrr.files.identical? .. deleting anyway");
                        wastebin.deleteUnknown(target);
                    }
                }
            }
            indexer.ignorePath(target.getAbsolutePath(), 1);
            ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(source);
            fsTarget.getiNode().v(modifiedAndInode.getiNode());
            fsTarget.getModified().v(modifiedAndInode.getModified());
            fsTarget.getSize().v(source.length());
            fsTarget.getSynced().v(true);
            boolean moved = source.move(target);
            if (!moved || !target.exists())
                return null;
            fsDao.update(fsTarget);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //fsDao.unlockWrite();
        }
        return target;
    }

    public void suspend() {
        if (transferManager != null)
            this.transferManager.suspend();
    }

    public void onFileTransferFailed(String hash) {
        try {
            fsDao.lockRead();
            if (fsDao.desiresHash(hash)) {
                System.err.println(getClass().getSimpleName() + ".onFileTransferFailed() file with hash " + hash + " is required but failed to transfer");
            }
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        } finally {
            fsDao.unlockRead();
        }
    }

    /**
     * @param file File in working directory
     * @param v
     * @throws SqlQueriesException
     */
    public void onFileTransferred(AFile file, String hash) throws SqlQueriesException, IOException {
        try {
            fsDao.lockWrite();
            //todo debug
            if (hash.equals("fdcbc1aca23cfebaa128bac31df20969"))
                Lok.debug("SyncHandler.onFileTransferred.debug23423r");
            if (hash.equals("238810397cd86edae7957bca350098bc")) {
                Lok.debug("SyncHandler.onFileTransferred.debugjivf3hi8o");
            }
            if (hash.equals("0610338abac66dc659f5c04dc95a480a"))
                Lok.debug("SyncHandler.onFileTransferred.debugf43fh30w");
            List<FsFile> fsFiles = fsDao.getNonSyncedFilesByHash(hash);
            if (fsFiles.size() > 0) {
                //TODO check if file is in transfer dir, then move, else copy
                if (file.getAbsolutePath().startsWith(driveDatabaseManager.getDriveSettings().getRootDirectory().getPath())) {
                    FsFile fsFile = fsFiles.get(0);
                    file = moveFile(file, fsFile);
                    //todo debug
                    if (file == null)
                        Lok.debug("SyncHandler.onFileTransferred.debug.ja09gf4");
                    fsFile.getSynced().v(true);
                    fsDao.setSynced(fsFile.getId().v(), true);
                }
            }
            if (fsFiles.size() > 1) {
                for (int i = 1; i < fsFiles.size(); i++) {
                    //TODO debug stopped here last night
                    FsFile fsFile = fsFiles.get(i);
                    copyFile(file, fsFile);
                    fsDao.setSynced(fsFile.getId().v(), true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            fsDao.unlockWrite();
        }

    }

    private void copyFile(AFile source, FsFile fsTarget) throws SqlQueriesException, IOException {
        fsDao.lockRead();
        AFile target = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsTarget);
        fsDao.unlockRead();
        indexer.ignorePath(target.getAbsolutePath(), 2);
        //todo debug
        if (source == null)
            Lok.debug("SyncHandler.copyFile.debug1");
        InputStream in = source.inputStream();
        try {
            OutputStream out = target.outputStream();
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
            //indexer.stopIgnore(target.getAbsolutePath());
            RWLock waitLock = new RWLock();
            ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(target);
            fsTarget.getiNode().v(modifiedAndInode.getiNode());
            fsTarget.getModified().v(modifiedAndInode.getModified());
            fsTarget.getSize().v(target.length());
            driveDatabaseManager.getFsDao().update(fsTarget);
            waitLock.lockWrite();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            in.close();
        }
    }


    public void commitStage(Long stageSetId) throws OutOfSpaceException {
        commitStage(stageSetId, true);
    }


    public void commitStage(Long stageSetId, boolean lockFsEntry) throws OutOfSpaceException {
        commitStage(stageSetId, lockFsEntry, null);
    }

    /**
     * @param stageSetId
     */
    public void commitStage(Long stageSetId, boolean lockFsEntry, Map<Long, Long> stageIdFsIdMap) throws OutOfSpaceException {
        //todo debug
        if (stageSetId == 6 || stageSetId == 13)
            Lok.debug("SyncHandler.commitStage.debugj9v0jaseß");
        FsDao fsDao = driveDatabaseManager.getFsDao();
        StageDao stageDao = driveDatabaseManager.getStageDao();
        try {
            if (lockFsEntry)
                fsDao.lockWrite();
            long version = driveDatabaseManager.getDriveSettings().getLastSyncedVersion() + 1;
            StageSet stageSet = stageDao.getStageSetById(stageSetId);
            //check if sufficient space is available
            if (!stageSet.fromFs())
                quotaManager.freeSpaceForStageSet(stageSetId);
            N.sqlResource(stageDao.getStagesByStageSet(stageSetId), stages -> {
                Stage stage = stages.getNext();
                while (stage != null) {
                    //todo debug
                    if (stage.getName().equals("samedir"))
                        Lok.debug("SyncHandler.commitStag.debugn3uivw34e");
                    if (stage.getName().equals("sub2.txt"))
                        Lok.debug("SyncHandler.commitStag.debugl,b45ni");
                    if (stage.getFsId() == null) {
                        if (stage.getIsDirectory()) {
                            if (stage.getFsId() != null) {
                                FsDirectory dbDir = fsDao.getDirectoryById(stage.getId());
                                dbDir.getVersion().v(version);
                                dbDir.getContentHash().v(stage.getContentHash());
                                dbDir.getModified().v(stage.getModified());
                                fsDao.update(dbDir);
                            } else {
                                FsDirectory dir = new FsDirectory();
                                dir.getVersion().v(version);
                                dir.getContentHash().v(stage.getContentHash());
                                dir.getName().v(stage.getName());
                                dir.getModified().v(stage.getModified());
                                dir.getiNode().v(stage.getiNode());
                                Long fsParentId = null;
                                if (stage.getParentId() != null) {
                                    fsParentId = stageDao.getStageById(stage.getParentId()).getFsId();
                                } else if (stage.getFsParentId() != null)
                                    fsParentId = stage.getFsParentId();
                                dir.getParentId().v(fsParentId);
                                fsDao.insert(dir);
                                if (stageIdFsIdMap != null) {
                                    stageIdFsIdMap.put(stage.getId(), dir.getId().v());
                                }
                                this.createDirs(driveDatabaseManager.getDriveSettings().getRootDirectory(), dir);
                                stage.setFsId(dir.getId().v());
                            }
                        } else {
                            // it is a new file
                            FsFile fsFile = null;
                            if (stage.getFsId() != null)
                                fsFile = fsDao.getFile(stage.getFsId());
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
                            fsFile.getVersion().v(version);
                            fsFile.getModified().v(stage.getModified());
                            fsFile.getiNode().v(stage.getiNode());
                            fsFile.getSize().v(stage.getSize());
                            fsFile.getSynced().v(stage.getSynced());
                            fsDao.insert(fsFile);
                            if (stageIdFsIdMap != null) {
                                stageIdFsIdMap.put(stage.getId(), fsFile.getId().v());
                            }
                            stage.setFsId(fsFile.getId().v());
                        }
                    } else { // fs.id is not null
                        if (stage.getFsParentId() != null && !fsDao.hasId(stage.getFsParentId())) {//skip if parent was deleted
                            stage = stages.getNext();
                            continue;
                        }
                        //todo debug
                        if (stage.getSyncedPair().isNull())
                            Lok.debug("SyncHandler.commitStage.debugebguse0");
//                        if (stage.getDeleted() != null && stage.getDeleted() && stage.getIsDirectory())
//                            wastebin.delete(stage.getFsId()););
                        if ((stage.getDeleted() != null && stage.getDeleted() && stage.getSynced() != null && stage.getSynced()) || (stage.getIsDirectory() && stage.getDeleted())) {
                            //if (stage.getDeleted() != null && stage.getSynced() != null && (stage.getDeleted() && stage.getSynced())) {
                            //todo BUG: 3 Conflict solve dialoge kommen hoch, wenn hier Haltepunkt bei DriveFXTest.complectConflict() drin ist
                            wastebin.deleteFsEntry(stage.getFsId());
                        } else {
                            FsEntry fsEntry = stageDao.stage2FsEntry(stage, version);
                            // TODO inode & co
                            FsEntry oldeEntry = fsDao.getGenericById(fsEntry.getId().v());
                            if (oldeEntry != null && oldeEntry.getIsDirectory().v() && fsEntry.getIsDirectory().v()) {
                                fsEntry.getiNode().v(oldeEntry.getiNode());
                                fsEntry.getModified().v(oldeEntry.getModified());
                            }
                            if (fsEntry.getId().v() != null && !fsEntry.getIsDirectory().v()) {
                                FsFile oldeFsFile = fsDao.getFile(fsEntry.getId().v());
                                if (oldeFsFile != null && !stageSet.fromFs() && !fsEntry.getSynced().v()) {
                                    wastebin.deleteFsFile(oldeFsFile);
                                } else {
                                    // delete file. consider that it might be in the same state as the stage
                                    AFile stageFile = stageDao.getFileByStage(stage);
                                    if (stageFile.exists()) {
                                        ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(stageFile);
                                        if (stage.getiNode() == null || stage.getModified() == null ||
                                                !(modifiedAndInode.getiNode().equals(stage.getiNode()) && modifiedAndInode.getModified().equals(stage.getModified()))) {
                                            wastebin.deleteUnknown(stageFile);
                                            stage.setSynced(false);
                                            // we could search more recent stagesets to find some clues here and prevent deleteUnknown().
                                        }
                                        // else: the file is as we want it to be
                                    }
                                }
                            }
                            if (fsEntry.getSynced().isNull())
                                Lok.debug("SyncHandler.commitStage.isnull");
                            if (!fsEntry.getIsDirectory().v() && (stage.getSynced() != null && !stage.getSynced()))
                                fsEntry.getSynced().v(false);
//                            File file = stageDao.getFileByStage(stage);
//                            fsEntry.getSynced().v(file.exists());
                            fsDao.insertOrUpdate(fsEntry);
                            this.createDirs(driveDatabaseManager.getDriveSettings().getRootDirectory(), fsEntry);
                        }
                    }
                    stageDao.update(stage);
                    stage = stages.getNext();
                }
                driveDatabaseManager.updateVersion();
                stageDao.deleteStageSet(stageSetId);
                transferManager.research();
            });
            wastebin.maintenance();
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        } finally {
            if (lockFsEntry)
                fsDao.unlockWrite();
        }
    }


    private void createDirs(RootDirectory rootDirectory, FsEntry fsEntry) throws SqlQueriesException, IOException, InterruptedException {
        // assume that root directory already exists
        if (fsEntry.getParentId().v() == null)
            return;
        FsDao fsDao = driveDatabaseManager.getFsDao();
        Stack<FsDirectory> stack = new Stack<>();
        FsDirectory dbParent = fsDao.getDirectoryById(fsEntry.getParentId().v());
        while (dbParent != null && dbParent.getParentId().v() != null) {
            stack.add(dbParent);
            dbParent = fsDao.getDirectoryById(dbParent.getParentId().v());
        }
        String path = rootDirectory.getPath() + File.separator;
        if (!stack.empty()) {
            while (!stack.empty()) {
                dbParent = stack.pop();
                path += dbParent.getName().v();
                AFile d = AFile.instance(path);
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
            AFile target = AFile.instance(path);
            if (!target.exists()) {
                indexer.ignorePath(path, 1);
                Lok.debug("SyncHandler.createDirs: " + target.getAbsolutePath());
                target.mkdirs();
                indexer.watchDirectory(target);
                updateInodeModified(fsEntry, target);
            }
        }
    }

    private void updateInodeModified(FsEntry entry, AFile f) throws SqlQueriesException, IOException, InterruptedException {
        ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(f);
        entry.getiNode().v(modifiedAndInode.getiNode());
        entry.getModified().v(modifiedAndInode.getModified());
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
}

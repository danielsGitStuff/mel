package de.mein.drive.service.sync;

import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import de.mein.drive.DriveSettings;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.ModifiedAndInode;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.index.Indexer;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.WasteBin;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.transfer.TransferManager;
import de.mein.sql.ISQLResource;
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
    protected WasteBin wasteBin;

    public SyncHandler(MeinAuthService meinAuthService, MeinDriveService meinDriveService) {
        this.meinAuthService = meinAuthService;
        this.fsDao = meinDriveService.getDriveDatabaseManager().getFsDao();
        this.stageDao = meinDriveService.getDriveDatabaseManager().getStageDao();
        this.driveSettings = meinDriveService.getDriveSettings();
        this.meinDriveService = meinDriveService;
        this.driveDatabaseManager = meinDriveService.getDriveDatabaseManager();
        this.indexer = meinDriveService.getIndexer();
        this.wasteBin = meinDriveService.getWasteBin();
        this.transferManager = new TransferManager(meinAuthService, meinDriveService, meinDriveService.getDriveDatabaseManager().getTransferDao()
                , wasteBin, this);
    }

    public File moveFile(File source, FsFile fsTarget) throws SqlQueriesException, IOException {
        File target = null;
        try {
            //fsDao.lockWrite();
            target = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsTarget);
            System.out.println("SyncHandler.moveFile (" + source.getAbsolutePath() + ") -> (" + target.getAbsolutePath() + ")");
            // check if there already is a file & delete
            if (target.exists()) {
                ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(target);
                // file had to be marked as deleted before, which means the inode and so on appear in the wastebin
                Waste waste = meinDriveService.getDriveDatabaseManager().getWasteDao().getWasteByInode(modifiedAndInode.getiNode());
                GenericFSEntry genericFSEntry = fsDao.getGenericByINode(modifiedAndInode.getiNode());
                if (target.isFile()) {
                    if (waste != null) {
                        if (waste.getModified().v().equals(modifiedAndInode.getModified())) {
                            wasteBin.del(waste, target);
                        } else {
                            System.err.println("SyncHandler.moveFile: File was modified in the meantime :(");
                            System.err.println("SyncHandler.moveFile: " + target.getAbsolutePath());
                        }
                    }
                }
            }
            indexer.ignorePath(target.getAbsolutePath(), 1);
            ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(source);
            fsTarget.getiNode().v(modifiedAndInode.getiNode());
            fsTarget.getModified().v(modifiedAndInode.getModified());
            fsTarget.getSize().v(source.length());
            fsTarget.getSynced().v(true);
            boolean moved = source.renameTo(target);
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

    /**
     * @param file File in working directory
     * @param v
     * @throws SqlQueriesException
     */
    public void onFileTransferred(File file, String hash) throws SqlQueriesException, IOException {
        try {
            fsDao.lockWrite();
            List<FsFile> fsFiles = fsDao.getNonSyncedFilesByHash(hash);
            if (fsFiles.size() > 0) {
                //TODO check if file is in transfer dir, then move, else copy
                if (file.getAbsolutePath().startsWith(driveDatabaseManager.getDriveSettings().getTransferDirectoryPath())) {
                    FsFile fsFile = fsFiles.get(0);
                    file = moveFile(file, fsFile);
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

    private void copyFile(File source, FsFile fsTarget) throws SqlQueriesException, IOException {
        fsDao.lockRead();
        File target = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsTarget);
        fsDao.unlockRead();
        indexer.ignorePath(target.getAbsolutePath(), 2);
        InputStream in = new FileInputStream(source);
        try {
            OutputStream out = new FileOutputStream(target);
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


    public void commitStage(Long stageSetId) {
        commitStage(stageSetId, true);
    }


    public void commitStage(Long stageSetId, boolean lockFsEntry) {
        commitStage(stageSetId, lockFsEntry, null);
    }

    /**
     * @param stageSetId
     */
    public void commitStage(Long stageSetId, boolean lockFsEntry, Map<Long, Long> stageIdFsIdMap) {
        FsDao fsDao = driveDatabaseManager.getFsDao();
        StageDao stageDao = driveDatabaseManager.getStageDao();
        try {
            if (lockFsEntry)
                fsDao.lockWrite();
            long version = driveDatabaseManager.getDriveSettings().getLastSyncedVersion() + 1;
            StageSet stageSet = stageDao.getStageSetById(stageSetId);
            ISQLResource<Stage> stages = stageDao.getStagesByStageSet(stageSetId);
            Stage stage = stages.getNext();
            while (stage != null) {
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
                    if (stage.getDeleted() != null && stage.getDeleted()) {
                        //todo BUG: 3 Conflict solve dialoge kommen hoch, wenn hier Haltepunkt bei DriveFXTest.complectConflict() drin ist
                        wasteBin.delete(stage.getFsId());
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
                                wasteBin.deleteFile(oldeFsFile);
                            }
                        }
                        if (fsEntry.getSynced().isNull())
                            System.out.println("SyncHandler.commitStage.isnull");
                        if (!fsEntry.getIsDirectory().v() && (stage.getSynced() != null && !stage.getSynced()))
                            fsEntry.getSynced().v(false);
                        fsDao.insertOrUpdate(fsEntry);
                        this.createDirs(driveDatabaseManager.getDriveSettings().getRootDirectory(), fsEntry);
                    }
                }
                stageDao.update(stage);
                stage = stages.getNext();
            }
            driveDatabaseManager.updateVersion();
            stageDao.deleteStageSet(stageSetId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (lockFsEntry)
                fsDao.unlockWrite();
        }
    }


    private void createDirs(RootDirectory rootDirectory, FsEntry fsEntry) throws SqlQueriesException, IOException {
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
                File d = new File(path);
                if (!d.exists()) {
                    indexer.ignorePath(path, 1);
                    System.out.println("SyncHandler.createDirs: " + d.getAbsolutePath());
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
            File target = new File(path);
            if (!target.exists()) {
                indexer.ignorePath(path, 1);
                System.out.println("SyncHandler.createDirs: " + target.getAbsolutePath());
                target.mkdirs();
                indexer.watchDirectory(target);
                updateInodeModified(fsEntry, target);
            }
        }
    }

    private void updateInodeModified(FsEntry entry, File f) throws SqlQueriesException, IOException {
        ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(f);
        entry.getiNode().v(modifiedAndInode.getiNode());
        entry.getModified().v(modifiedAndInode.getModified());
        fsDao.update(entry);
    }

    public void start() {
        transferManager.start();
    }
}

package de.mein.drive.service;

import de.mein.auth.tools.N;
import de.mein.sql.Hash;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.ModifiedAndInode;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.Indexer;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.sql.dao.WasteDao;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * erases files and directories. moves files to the wastebin folder and keeps track of the wastebin folders content.
 * Created by xor on 1/27/17.
 */
public class Wastebin {
    private final File wasteDir;
    private final MeinDriveService meinDriveService;
    private final DriveSettings driveSettings;
    private final FsDao fsDao;
    private final Indexer indexer;
    private final DriveDatabaseManager driveDatabaseManager;
    private final WasteDao wasteDao;
    private final StageDao stageDao;
    private final File deferredDir;

    public Wastebin(MeinDriveService meinDriveService) {
        this.driveDatabaseManager = meinDriveService.getDriveDatabaseManager();
        this.meinDriveService = meinDriveService;
        this.fsDao = driveDatabaseManager.getFsDao();
        this.stageDao = driveDatabaseManager.getStageDao();
        this.driveSettings = meinDriveService.getDriveSettings();
        this.indexer = meinDriveService.getIndexer();
        this.wasteDao = driveDatabaseManager.getWasteDao();
        this.wasteDir = new File(driveSettings.getTransferDirectoryPath() + File.separator + DriveStrings.WASTEBIN);
        this.deferredDir = new File(wasteDir, "deferred");
        wasteDir.mkdirs();
        deferredDir.mkdirs();
    }

    /**
     * will delete everything older than the maximum allowed age or more if max wastebin size is exceeded.<br>
     * See {@link DriveSettings}
     */
    public void maintenance() throws SqlQueriesException {
        final Long maxAge = driveSettings.getMaxAge();
        final Long maxSize = driveSettings.getMaxWastebinSize();
        if (maxSize == null){
            System.err.println("Wastebin.maintenance.ERROR: DriveSettings.maxwastebinsize not set!");
        }
        N.readSqlResource(wasteDao.getOlderThanResource(maxAge), (sqlResource, waste) -> rm(waste));
        wasteDao.deleteFlagged();
        final Long[] size = {wasteDao.getSize()};
        if (size[0] > maxSize) {
            N.readSqlResource(wasteDao.getAgeSortedResource(), (sqlResource, waste) -> {
                rm(waste);
                size[0] = size[0] - waste.getSize().v();
                if (size[0] < maxSize)
                    sqlResource.close();
            });
        }
        wasteDao.deleteFlagged();
    }

    public void flagDeleted(long id, boolean flag) throws SqlQueriesException {
        wasteDao.flagDeleted(id, flag);
    }

    public void deleteFromWastebin(Long wasteId) {

    }

    public void deleteFsEntry(Long fsId) throws SqlQueriesException, IOException {
        GenericFSEntry genericFSEntry = fsDao.getGenericById(fsId);
        //todo debug
        if (genericFSEntry == null)
            System.out.println("Wastebin.deleteFsEntry.debug1");
        if (genericFSEntry != null) {
            if (genericFSEntry.getIsDirectory().v())
                deleteDirectory((FsDirectory) genericFSEntry.ins());
            else
                deleteFsFile((FsFile) genericFSEntry.ins());
        }
    }

    /**
     * scratches waste from disk. flags as deleted.
     *
     * @param wasteId
     */
    public void rm(Long wasteId) throws SqlQueriesException {
        Waste waste = wasteDao.getWasteById(wasteId);
        rm(waste);
    }

    /**
     * scratches waste from disk. flags as deleted.
     *
     * @param waste
     */
    public void rm(Waste waste) throws SqlQueriesException {
        File target = getWasteFile(waste);
        target.delete();
        if (target.exists())
            N.r(() -> BashTools.rmRf(target));
        wasteDao.flagDeleted(waste.getId().v(), true);
    }

    private void deleteDirectory(FsDirectory fsDirectory) throws SqlQueriesException, IOException {
        File f = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsDirectory);
        if (f.exists()) {
            indexer.ignorePath(f.getAbsolutePath(), 1);
            if (f.isDirectory()) {
                recursiveDelete(f);
                BashTools.rmRf(f);
            } else {
                //todo directory might have been replaced by a file
                System.err.println("Wastebin.deleteDirectory.DIRECTORY.REPLACED.BY.FILE: " + f.getAbsolutePath());
                BashTools.rmRf(f);
            }
        }
        driveDatabaseManager.getFsDao().deleteById(fsDirectory.getId().v());
    }

    /**
     * moves file to wastebin without any hesitations
     *
     * @param waste
     * @param file
     */
    public File moveToBin(Waste waste, File file) throws SqlQueriesException {
        try {
            File target = new File(driveSettings.getTransferDirectoryPath() + File.separator + DriveStrings.WASTEBIN + File.separator + waste.getHash().v() + "." + waste.getId().v());
            file.renameTo(target);
            waste.getInplace().v(true);
            wasteDao.update(waste);
            return target;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteFsFile(FsFile fsFile) {
        try {
            File f = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsFile);
            if (f.exists()) {
                indexer.ignorePath(f.getAbsolutePath(), 1);
                if (f.isFile()) {
                    ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(f);
                    Waste waste = wasteDao.getWasteByInode(modifiedAndInode.getiNode());
                    if (waste != null) {
                        // we once wanted this file to be deleted. check if it did not change in the meantime
                        if (waste.getInode().v().equals(fsFile.getiNode().v()) && waste.getModified().v().equals(fsFile.getModified().v())) {
                            moveToBin(waste, f);
                        } else {
                            // it changed :(
                            System.err.println("Wastebin.deleteFilr5436t34e");
                        }
                    } else if (fsFile.getSynced().v()) {
                        waste = wasteDao.fsToWaste(fsFile);
                        moveToBin(waste, f);
                    }

                } else {
                    //todo file might have been replaced by a directory
                    //we do not know about its contents and therefore will delete it
                    //might trigger the indexlistener
                    System.err.println("Wastebin.deleteFsFile.FILE.REPLACED.BY.DIRECTORY: " + f.getAbsolutePath());
                    BashTools.rmRf(f);
                }
            }
            driveDatabaseManager.getFsDao().deleteById(fsFile.getId().v());
        } catch (Exception e) {
            System.err.println("Wastebin.deleteFsFile.failed");
            e.printStackTrace();
        }
    }

    private String findHashOfFile(File file, Long inode) throws IOException, SqlQueriesException {
        GenericFSEntry genFsFile = fsDao.getGenericByINode(inode);
        Stage stage = stageDao.getLatestStageFromFsByINode(inode);
        if (stage != null) {
            return stage.getContentHash();
        }
        if (genFsFile != null)
            return genFsFile.getContentHash().v();
        return null;
    }

    private void moveToBin(File file, String contentHash, ModifiedAndInode modifiedAndInode) throws SqlQueriesException {
        //todo debug
        if (contentHash.equals("9471e9c1779a51bb6fcb5735127c0701"))
            System.out.println("Wastebin.moveToBin.debugjfc03jg0w");
        Waste waste = new Waste();
        waste.getModified().v(modifiedAndInode.getModified());
        waste.getHash().v(contentHash);
        waste.getInode().v(modifiedAndInode.getiNode());
        waste.getInplace().v(false);
        waste.getName().v(file.getName());
        waste.getSize().v(file.length());
        wasteDao.insert(waste);
        indexer.ignorePath(file.getAbsolutePath(), 1);
        moveToBin(waste, file);
    }

    private void recursiveDelete(File dir) throws SqlQueriesException, IOException {
        //todo debug
        if (dir.getAbsolutePath().equals("/home/xor/Documents/dev/IdeaProjects/drive/drivefx/testdir2/samedir/samesub"))
            System.out.println("Wastebin.recursiveDelete.debugnfi34fa");
        FsDirectory fsDirectory = fsDao.getFsDirectoryByPath(dir);
        File[] files = dir.listFiles(File::isFile);
        for (File f : files) {
            ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(f);
            String contentHash = findHashOfFile(f, modifiedAndInode.getiNode());
            if (contentHash != null) {
                moveToBin(f, contentHash, modifiedAndInode);
            } else {
                System.err.println("Wastebin.recursiveDelete.0ig4");
                f.delete();
            }
        }
        File[] subDirs = dir.listFiles(File::isDirectory);
        for (File subDir : subDirs) {
            indexer.ignorePath(subDir.getAbsolutePath(), 1);
            FsDirectory fsSubDir = fsDao.getSubDirectoryByName(fsDirectory.getId().v(), subDir.getName());
            recursiveDelete(subDir);
        }
    }

    protected String createWasteBinPath() {
        return wasteDir.getAbsolutePath() + File.separator;
    }

    public String getWasteLocationPath() {
        return wasteDir.getAbsolutePath();
    }

    private List<String> searchTransfer() throws SqlQueriesException {
        wasteDao.lockRead();
        try {
            return wasteDao.searchTransfer();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            wasteDao.unlockRead();
        }
    }


    public void restoreFsFiles(SyncHandler syncHandler) throws SqlQueriesException, IOException {
        List<String> availableHashes = searchTransfer();
        fsDao.lockWrite();
        for (String hash : availableHashes) {
            List<FsFile> fsFiles = fsDao.getNonSyncedFilesByHash(hash);
            for (FsFile fsFile : fsFiles) {
                Waste waste = wasteDao.getWasteByHash(fsFile.getContentHash().v());
                if (waste != null) {
                    File wasteFile = new File(wasteDir.getAbsolutePath() + File.separator + waste.getHash().v() + "." + waste.getId().v());
                    wasteDao.delete(waste.getId().v());
                    fsDao.setSynced(fsFile.getId().v(), true);
                    syncHandler.moveFile(wasteFile, fsFile);
                } else {
                    //todo debug
                    System.err.println("Wastebin.restoreFsFiles.degubgseo5ß");
                }
            }
        }
        fsDao.unlockWrite();
    }


    public File getByHash(String hash) throws SqlQueriesException {
        Waste waste = wasteDao.getWasteByHash(hash);
        if (waste != null)
            return getWasteFile(waste);
        return null;
    }

    private File getWasteFile(Waste waste) {
        //todo debug
        if (waste == null || waste.getHash().isNull())
            System.out.println("Wastebin.getWasteFile.debug.1");
        return new File(wasteDir.getAbsolutePath() + File.separator + waste.getHash().v() + "." + waste.getId().v());
    }

    /**
     * deletes the file immediately but does not assume its content(hash).
     * the content hash is determined deferred.
     *
     * @param file
     * @throws IOException
     */
    public void deleteUnknown(File file) throws IOException, SqlQueriesException {
        ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(file);
        File target = new File(deferredDir, modifiedAndInode.getiNode().toString());
        file.renameTo(target);
        //todo hashing is blocking yet!
        String hash = Hash.md5(target);
        Waste waste = new Waste();
        waste.getHash().v(hash);
        waste.getInode().v(modifiedAndInode.getiNode());
        waste.getModified().v(modifiedAndInode.getModified());
        waste.getInplace().v(false);
        waste.getSize().v(target.length());
        waste.getName().v(file.getName());
        wasteDao.insert(waste);
        File movedTo = this.moveToBin(waste, target);
        //todo tell a worker to investigate the deferred directory
        //todo worker has to tell the drive service or transfermanager that a file has been found
        //meinDriveService.syncHandler.onFileTransferred(movedTo, hash);
    }

    public void deleteFlagged() throws SqlQueriesException {
        wasteDao.deleteFlagged();
    }
}

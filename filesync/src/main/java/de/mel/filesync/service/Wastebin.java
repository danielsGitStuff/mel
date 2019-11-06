package de.mel.filesync.service;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.index.Indexer;
import de.mel.filesync.service.sync.SyncHandler;
import de.mel.filesync.sql.*;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.filesync.sql.dao.WasteDao;
import de.mel.sql.Hash;
import de.mel.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * erases files and directories. moves files to the wastebin folder and keeps track of the wastebin folders content.
 * Created by xor on 1/27/17.
 */
public class Wastebin {
    private final AbstractFile wasteDir;
    private final MelFileSyncService melFileSyncService;
    private final FileSyncSettings fileSyncSettings;
    private final FsDao fsDao;
    private final Indexer indexer;
    private final FileSyncDatabaseManager fileSyncDatabaseManager;
    private final WasteDao wasteDao;
    private final StageDao stageDao;
    private final AbstractFile deferredDir;
    private SyncHandler syncHandler;

    public Wastebin(MelFileSyncService melFileSyncService) {
        this.fileSyncDatabaseManager = melFileSyncService.getFileSyncDatabaseManager();
        this.melFileSyncService = melFileSyncService;
        this.fsDao = fileSyncDatabaseManager.getFsDao();
        this.stageDao = fileSyncDatabaseManager.getStageDao();
        this.fileSyncSettings = melFileSyncService.getFileSyncSettings();
        this.indexer = melFileSyncService.getIndexer();
        this.wasteDao = fileSyncDatabaseManager.getWasteDao();
        this.wasteDir = AbstractFile.instance(fileSyncSettings.getTransferDirectoryFile(), FileSyncStrings.WASTEBIN);
        this.deferredDir = AbstractFile.instance(wasteDir, "deferred");
        wasteDir.mkdirs();
        deferredDir.mkdirs();
    }

    /**
     * will delete everything older than the maximum allowed age or more if max wastebin size is exceeded.<br>
     * See {@link FileSyncSettings}
     */
    public void maintenance() throws SqlQueriesException {
        final Long maxAge = fileSyncSettings.getMaxAge();
        final Long maxSize = fileSyncSettings.getMaxWastebinSize();
        if (maxSize == null) {
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

    public void deleteFsEntry(Long fsId) throws SqlQueriesException, IOException, InterruptedException {
        GenericFSEntry genericFSEntry = fsDao.getGenericById(fsId);
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
        AbstractFile target = getWasteFile(waste);
        target.delete();
        if (target.exists())
            N.r(() -> BashTools.rmRf(target));
        wasteDao.flagDeleted(waste.getId().v(), true);
    }

    private void deleteDirectory(FsDirectory fsDirectory) throws SqlQueriesException, IOException, InterruptedException {
        AbstractFile f = fsDao.getFileByFsFile(fileSyncSettings.getRootDirectory(), fsDirectory);
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
        fileSyncDatabaseManager.getFsDao().deleteById(fsDirectory.getId().v());
    }

    /**
     * moves file to wastebin without any hesitations
     *
     * @param waste
     * @param file
     */
    public AbstractFile moveToBin(Waste waste, AbstractFile file) throws SqlQueriesException {
        try {
            AbstractFile target = AbstractFile.instance(wasteDir, waste.getHash().v() + "." + waste.getId().v());
            syncHandler.getFileDistributor().moveBlocking(file, target, null);
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
            AbstractFile f = fsDao.getFileByFsFile(fileSyncSettings.getRootDirectory(), fsFile);
            if (f.exists()) {
                indexer.ignorePath(f.getAbsolutePath(), 1);
                if (f.isFile()) {
                    FsBashDetails fsBashDetails = BashTools.getFsBashDetails(f);
                    Waste waste = wasteDao.getWasteByInode(fsBashDetails.getiNode());
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
            fileSyncDatabaseManager.getFsDao().deleteById(fsFile.getId().v());
        } catch (Exception e) {
            System.err.println("Wastebin.deleteFsFile.failed");
            e.printStackTrace();
        }
    }

    private String findHashOfFile(AbstractFile file, Long inode) throws IOException, SqlQueriesException {
        GenericFSEntry genFsFile = fsDao.getGenericByINode(inode);
        Stage stage = stageDao.getLatestStageFromFsByINode(inode);
        if (stage != null) {
            return stage.getContentHash();
        }
        if (genFsFile != null)
            return genFsFile.getContentHash().v();
        return null;
    }

    private void moveToBin(AbstractFile file, String contentHash, FsBashDetails fsBashDetails) throws SqlQueriesException {
        Waste waste = new Waste();
        waste.getModified().v(fsBashDetails.getModified());
        waste.getHash().v(contentHash);
        waste.getInode().v(fsBashDetails.getiNode());
        waste.getInplace().v(false);
        waste.getName().v(file.getName());
        waste.getSize().v(file.length());
        waste.getFlagDelete().v(false);
        wasteDao.insert(waste);
        indexer.ignorePath(file.getAbsolutePath(), 1);
        moveToBin(waste, file);
    }

    private void recursiveDelete(AbstractFile dir) throws SqlQueriesException, IOException, InterruptedException {
        FsDirectory fsDirectory = fsDao.getFsDirectoryByPath(dir);
        AbstractFile[] files = dir.listFiles();
        for (AbstractFile f : files) {
            FsBashDetails fsBashDetails = BashTools.getFsBashDetails(f);
            String contentHash = findHashOfFile(f, fsBashDetails.getiNode());
            if (contentHash != null) {
                moveToBin(f, contentHash, fsBashDetails);
            } else {
                System.err.println("Wastebin.recursiveDelete.0ig4");
                f.delete();
            }
        }
        AbstractFile[] subDirs = dir.listDirectories();
        for (AbstractFile subDir : subDirs) {
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
        Warden<List<String>> warden = P.confine(P.read(wasteDao));
        List<String> result = warden.runResult(wasteDao::searchTransfer).get();
        warden.end();
        return result;
//        wasteDao.lockRead();
//        try {
//            return wasteDao.searchTransfer();
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw e;
//        } finally {
//            wasteDao.unlockRead();
//        }
    }


    public void restoreFsFiles() throws SqlQueriesException, IOException {
        List<String> availableHashes = searchTransfer();
        Warden warden = P.confine(fsDao);
        try {
            for (String hash : availableHashes) {
                List<FsFile> fsFiles = fsDao.getNonSyncedFilesByHash(hash);
                for (FsFile fsFile : fsFiles) {
                    Waste waste = wasteDao.getWasteByHash(fsFile.getContentHash().v());
                    if (waste != null) {
                        AbstractFile wasteFile = AbstractFile.instance(wasteDir.getAbsolutePath() + File.separator + waste.getHash().v() + "." + waste.getId().v());
                        wasteDao.delete(waste.getId().v());
                        fsDao.setSynced(fsFile.getId().v(), true);
                        syncHandler.getFileDistributor().moveBlocking(wasteFile, fsDao.getFileByFsFile(fileSyncSettings.getRootDirectory(), fsFile), fsFile.getId().v());
                    } else {
                        Lok.error("Wastebin.restoreFsFiles");
                    }
                }
            }
        } finally {
            warden.end();
        }

    }


    public AbstractFile getByHash(String hash) throws SqlQueriesException {
        Waste waste = wasteDao.getWasteByHash(hash);
        if (waste != null)
            return getWasteFile(waste);
        return null;
    }

    private AbstractFile getWasteFile(Waste waste) {
        return AbstractFile.instance(wasteDir.getAbsolutePath() + File.separator + waste.getHash().v() + "." + waste.getId().v());
    }

    /**
     * deletes the file immediately but does not assume its content(hash).
     * the content hash is determined afterwards.
     * This method might crash in between (eg. if a file has been deleted by now)
     *
     * @param file
     * @throws IOException
     */
    public void deleteUnknown(AbstractFile file) throws SqlQueriesException, IOException, InterruptedException {
        FsBashDetails fsBashDetails = BashTools.getFsBashDetails(file);
        AbstractFile target = AbstractFile.instance(deferredDir, fsBashDetails.getiNode().toString());
        syncHandler.getFileDistributor().moveBlocking(file, target, null);
        //if dir?!?!
        if (target.isDirectory()) {
            for (AbstractFile f : target.listContent()) {
                deleteUnknown(f);
            }
            target.delete();
            return;
        }

        //todo hashing is blocking yet!
        String hash = Hash.md5(target.inputStream());
        Waste waste = new Waste();
        waste.getHash().v(hash);
        waste.getInode().v(fsBashDetails.getiNode());
        waste.getModified().v(fsBashDetails.getModified());
        waste.getInplace().v(false);
        waste.getSize().v(target.length());
        waste.getName().v(file.getName());
        waste.getFlagDelete().v(false);
        wasteDao.insert(waste);
        AbstractFile movedTo = this.moveToBin(waste, target);
        //todo tell a worker to investigate the deferred directory
        //todo worker has to tell the drive service or transfermanager that a file has been found
        //melDriveService.syncHandler.onFileTransferred(movedTo, hash);
    }

    public void deleteFlagged() throws SqlQueriesException {
        wasteDao.deleteFlagged();
    }

    public void setSyncHandler(SyncHandler syncHandler) {
        this.syncHandler = syncHandler;
    }
}

package de.mein.drive.service;

import de.mein.drive.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.BashTools;
import de.mein.drive.index.Indexer;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.sql.dao.WasteDao;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * erases files and directories. moves files to the wastebin folder and keeps track of the wastebin folders content.
 * Created by xor on 1/27/17.
 */
public class WasteBin {
    private final File wasteDir;
    private final MeinDriveService meinDriveService;
    private final DriveSettings driveSettings;
    private final FsDao fsDao;
    private final Indexer indexer;
    private final DriveDatabaseManager driveDatabaseManager;
    private final WasteDao wasteDao;
    private final StageDao stageDao;

    public WasteBin(MeinDriveService meinDriveService) {
        this.driveDatabaseManager = meinDriveService.getDriveDatabaseManager();
        this.meinDriveService = meinDriveService;
        this.fsDao = driveDatabaseManager.getFsDao();
        this.stageDao = driveDatabaseManager.getStageDao();
        this.driveSettings = meinDriveService.getDriveSettings();
        this.indexer = meinDriveService.getIndexer();
        this.wasteDao = driveDatabaseManager.getWasteDao();
        this.wasteDir = new File(driveSettings.getTransferDirectoryPath() + File.separator + DriveStrings.WASTEBIN);
        wasteDir.mkdirs();
    }


    public void delete(Long fsId) throws SqlQueriesException, IOException {
        GenericFSEntry genericFSEntry = fsDao.getGenericById(fsId);
        //todo debug
        if (genericFSEntry == null || genericFSEntry.getIsDirectory().isNull())
            System.out.println("WasteBin.delete.debugjfc3h0fg");
        if (genericFSEntry.getIsDirectory().v())
            deleteDirectory((FsDirectory) genericFSEntry.ins());
        else
            deleteFile((FsFile) genericFSEntry.ins());
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
     * @param file
     * @param waste
     */
    public void del(Waste waste, File file) throws SqlQueriesException {
        try {
            //todo debug
            if (waste.getHash().v().equals("51037a4a37730f52c8732586d3aaa316"))
                System.err.println("WasteBin.del.debug30tjf");
            File target = new File(driveSettings.getTransferDirectoryPath() + File.separator + DriveStrings.WASTEBIN + File.separator + waste.getHash().v() + "." + waste.getId().v());
            file.renameTo(target);
            waste.getInplace().v(true);
            wasteDao.update(waste);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteFile(FsFile fsFile) {
        // todo debug
        if (fsFile.getContentHash().v().equals("51037a4a37730f52c8732586d3aaa316") || fsFile.getName().v().equals("same1.txt"))
            System.err.println("WasteBin.deleteFile.debug3434");
        try {
            File f = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsFile);
            if (f.exists()) {
                indexer.ignorePath(f.getAbsolutePath(), 1);
                if (f.isFile()) {
                    Long inode = BashTools.getINodeOfFile(f);
                    Waste waste = wasteDao.getWasteByInode(inode);
                    if (waste != null) {
                        // we once wanted this file to be deleted. check if it did not change in the meantime
                        if (waste.getInode().v().equals(fsFile.getiNode().v()) && waste.getModified().v().equals(fsFile.getModified().v())) {
                            del(waste, f);
                        } else {
                            // it changed :(
                            System.err.println("WasteBin.deleteFilr5436t34e");
                        }
                    } else {
                        waste = wasteDao.fsToWaste(fsFile);
                        del(waste, f);
                    }

                } else {
                    //todo file might have been replaced by a directory
                    //we do not know about its contents and therefore will delete it
                    //might trigger the indexlistener
                    System.err.println("Wastebin.deleteFile.FILE.REPLACED.BY.DIRECTORY: " + f.getAbsolutePath());
                    BashTools.rmRf(f);
                }
            }
            driveDatabaseManager.getFsDao().deleteById(fsFile.getId().v());
        } catch (Exception e) {
            System.err.println("WasteBin.deleteFile.failed");
            e.printStackTrace();
        }
    }

    private String findHashOfFile(File file, Long inode) throws IOException, SqlQueriesException {
        GenericFSEntry genFsFile = fsDao.getGenericByINode(inode);
        if (genFsFile != null)
            return genFsFile.getContentHash().v();
        Stage stage = stageDao.getLatestStageFromFsByINode(inode);
        if (stage != null) {
            return stage.getContentHash();
        }
        return null;
    }

    private void moveToBin(File file, String contentHash, Long inode) throws SqlQueriesException {
        Waste waste = new Waste();
        waste.getModified().v(file.lastModified());
        waste.getHash().v(contentHash);
        waste.getInode().v(inode);
        waste.getInplace().v(false);
        waste.getName().v(file.getName());
        waste.getSize().v(file.length());
        wasteDao.insert(waste);
        indexer.ignorePath(file.getAbsolutePath(), 1);
        del(waste, file);
    }

    private void recursiveDelete(File dir) throws SqlQueriesException, IOException {
        FsDirectory fsDirectory = fsDao.getFsDirectoryByPath(dir);
        File[] files = dir.listFiles(File::isFile);
        for (File f : files) {
            Long inode = BashTools.getINodeOfFile(f);
            String contentHash = findHashOfFile(f, inode);
            if (contentHash != null) {
                moveToBin(f, contentHash, inode);
            } else {
                System.err.println("WasteBin.recursiveDelete.0ig4");
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

    public String getWastePath() {
        return wasteDir.getAbsolutePath();
    }

    public List<String> searchTransfer() throws SqlQueriesException {
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

    public File getFileByHash(String hash) {
        return new File(wasteDir + File.separator + hash);
    }

    public void prepareDelete(FsFile oldeEntry) throws SqlQueriesException {
        wasteDao.fsToWaste(oldeEntry);
    }
}

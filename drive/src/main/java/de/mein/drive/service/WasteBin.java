package de.mein.drive.service;

import de.mein.drive.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.BashTools;
import de.mein.drive.index.Indexer;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
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

    public WasteBin(MeinDriveService meinDriveService) {
        this.driveDatabaseManager = meinDriveService.getDriveDatabaseManager();
        this.meinDriveService = meinDriveService;
        this.fsDao = driveDatabaseManager.getFsDao();
        this.driveSettings = meinDriveService.getDriveSettings();
        this.indexer = meinDriveService.getIndexer();
        this.wasteDao = driveDatabaseManager.getWasteDao();
        this.wasteDir = new File(driveSettings.getTransferDirectoryPath() + File.separator + DriveStrings.WASTEBIN);
        wasteDir.mkdirs();
    }


    public void delete(Long fsId) throws SqlQueriesException, IOException {
        GenericFSEntry genericFSEntry = fsDao.getGenericById(fsId);
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
                FsDirectory fsSubDirectory = fsDao.getFsDirectoryById(fsDirectory.getId().v());
                recursiveDelete(fsSubDirectory, f);
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
    public void del(Waste waste,File file) throws SqlQueriesException {
        try {
            File target = new File(driveSettings.getTransferDirectoryPath() + File.separator + DriveStrings.WASTEBIN + File.separator + waste.getHash().v() + "." + waste.getId().v());
            file.renameTo(target);
            waste.getInplace().v(true);
            wasteDao.update(waste);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void deleteFile(FsFile fsFile) throws SqlQueriesException, IOException {
        File f = fsDao.getFileByFsFile(driveSettings.getRootDirectory(), fsFile);
        if (f.exists()) {
            indexer.ignorePath(f.getAbsolutePath(), 1);
            if (f.isFile()) {
                Long inode = BashTools.getINodeOfFile(f);
                Waste waste = wasteDao.getWasteByInode(inode);
                if (waste != null) {
                    // we once wanted this file to be deleted. check if it did not change in the meantime
                    if (waste.getInode().v().equals(fsFile.getiNode().v()) && waste.getModified().v().equals(fsFile.getModified().v())) {
                        del(waste,f);
                    } else {
                        // it changed :(
                        System.err.println("WasteBin.deleteFilr5436t34e");
                    }
                } else {
                    waste = wasteDao.fsToWaste(fsFile);
                    del(waste,f);
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
    }

    private void recursiveDelete(FsDirectory fsDirectory, File dir) throws SqlQueriesException {
        if (fsDirectory != null) {
            File[] files = dir.listFiles(File::isFile);
            for (File f : files) {
                FsFile fsFile = fsDao.getFileByName(fsDirectory.getId().v(), f.getName());
                indexer.ignorePath(f.getAbsolutePath(), 1);
                if (fsFile != null) {
                    f.renameTo(new File(createWasteBinPath() + fsFile.getContentHash().v()));
                } else {
                    f.delete();
                }
            }
            File[] subDirs = dir.listFiles(File::isDirectory);
            for (File subDir : subDirs) {
                indexer.ignorePath(subDir.getAbsolutePath(), 1);
                FsDirectory fsSubDir = fsDao.getSubDirectoryByName(fsDirectory.getId().v(), subDir.getName());
                recursiveDelete(fsSubDir, subDir);
            }
        } else {
            System.out.println("MeinDriveService.recursiveDelete.was ganz böses :(");
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


package de.mein.drive.sql.dao;

import de.mein.auth.tools.RWSemaphore;
import de.mein.drive.DriveSettings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.sql.*;
import de.mein.sql.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class FsDao extends Dao {

    private RWSemaphore rwSemaphore = new RWSemaphore();
    private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private int rcount = 0;
    private int urcount = 0;
    private int wcount = 0;
    private int uwcount = 0;

    public void lockRead() {
        rwLock.readLock().lock();
        rcount++;
    }


    public void lockWrite() {
        rwLock.writeLock().lock();
        wcount++;
    }


    public void unlockRead() {
        rwLock.readLock().unlock();
        urcount++;
    }


    public void unlockWrite() {
        rwLock.writeLock().unlock();
        uwcount++;
    }

    private final DriveDatabaseManager driveDatabaseManager;
    private DriveSettings driveSettings;

    public FsDao(DriveDatabaseManager driveDatabaseManager, ISQLQueries ISQLQueries) {
        super(ISQLQueries);
        this.driveDatabaseManager = driveDatabaseManager;
    }


    public void update(FsEntry leFile) throws SqlQueriesException {
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(leFile.getId().v());
        sqlQueries.update(leFile, leFile.getId().k() + "=?", whereArgs);
    }

    public FsFile getFileByName(FsFile fsFile) throws SqlQueriesException {
        Long id = fsFile.getParentId().v();
        String where = "";
        List<Object> whereArguments = new ArrayList<>();
        if (id == null) {
            where = fsFile.getParentId().k() + " is null";
        } else {
            where = fsFile.getParentId().k() + "=?";
            whereArguments.add(id);
        }
        where += " and " + fsFile.getIsDirectory().k() + "=?"
                + " and " + fsFile.getName().k() + "=?";
        whereArguments.add(0);
        whereArguments.add(fsFile.getName().v());
        List<FsFile> tableObjects = sqlQueries.load(fsFile.getAllAttributes(), fsFile, where, whereArguments);
        if (tableObjects.size() == 0) {
            return null;
        } else {
            return tableObjects.get(0);
        }
    }

    public FsFile getFileByName(Long dirId, String name) throws SqlQueriesException {
        FsFile fsFile = new FsFile();
        fsFile.getParentId().v(dirId);
        fsFile.getName().v(name);
        return getFileByName(fsFile);
    }

    public List<FsFile> getFilesByHash(String hash) throws SqlQueriesException {
        FsFile dummy = new FsFile();
        String where = dummy.getContentHash().k() + "=?";
        List<FsFile> fsFiles = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(hash));
        return fsFiles;
    }

    public List<FsFile> getFilesByFsDirectory(Long id) throws SqlQueriesException {
        FsFile file = new FsFile();

        String where = "";
        List<Object> whereArguments = new ArrayList<>();
        if (id == null) {
            where = file.getParentId().k() + " is null";
        } else {
            where = file.getParentId().k() + "=?";
            whereArguments.add(id);
        }
        where += " and " + file.getIsDirectory().k() + "=?";
        whereArguments.add(0);
        List<FsFile> result = sqlQueries.load(file.getAllAttributes(), file, where, whereArguments);
        return result;
    }

    public void markFileMissed(FsFile f) throws SqlQueriesException {
        f.getVersion().v((Long) null);
        update(f);
    }

/*
    public List<FsFile> getByDirectorySync(Long id, Long syncId) throws SqlQueriesException {
        FsFile file = new FsFile();
        String where = file.getParentId().k() + "=? and " + file.getVersion().k() + ">?";
        List<Object> whereArguments = new ArrayList<>();
        whereArguments.add(id);
        whereArguments.add(syncId);
        List<SQLTableObject> result = sqlQueries.load(file.getAllAttributes(), file, where, whereArguments);
        return result;
    }*/

    public FsFile getFile(Long id) throws SqlQueriesException {
        FsFile fsFile = new FsFile();
        String where = fsFile.getId().k() + "=?";
        List<Object> whereArguments = new ArrayList<>();
        whereArguments.add(id);
        List<FsFile> tableObjects = sqlQueries.load(fsFile.getAllAttributes(), fsFile, where, whereArguments);
        if (tableObjects.size() == 0) {
            return null;
        } else {
            return tableObjects.get(0);
        }
    }


    public FsEntry insert(FsEntry fsEntry) throws SqlQueriesException {
        Long id;
        if (fsEntry.getId().v() != null)
            id = sqlQueries.insertWithAttributes(fsEntry, fsEntry.getAllAttributes());
        else
            id = sqlQueries.insert(fsEntry);
        fsEntry.getId().v(id);
        return fsEntry;
    }

    public FsFile insertLeFile(FsFile fsFile) throws SqlQueriesException {
        Long id = new Long(sqlQueries.insert(fsFile));
        fsFile.getId().v(id);
        return fsFile;
        //new MetaDao(sqlQueries, lock).updateSyncId(fsFile);
    }

    // directory stuff
    public FsDirectory insertLeDirectory(FsDirectory fsDirectory) throws SqlQueriesException {
        Long id = sqlQueries.insert(fsDirectory);
        fsDirectory.getId().v(id);
        return fsDirectory;
    }

    public List<FsDirectory> getSubDirectoriesByParentId(Long id) throws SqlQueriesException {
        FsDirectory dir = new FsDirectory();

        String where = "";
        List<Object> whereArgs = new ArrayList<>();
        if (id == null) {
            where = dir.getParentId().k() + " is null";
        } else {
            where = dir.getParentId().k() + "=?";
            whereArgs.add(id);
        }
        where += " and " + dir.getIsDirectory().k() + "=?";
        whereArgs.add(1);

        List<FsDirectory> result = sqlQueries.load(dir.getAllAttributes(), dir, where, whereArgs);
        return result;
    }


    public FsDirectory getSubDirectory(FsDirectory parent, FsDirectory directory) throws SqlQueriesException {
        String where = "";
        List<Object> whereArgs = new ArrayList<>();
        if (parent.getParentId().v() == null) {
            where = directory.getParentId().k() + " is null";
        } else {
            where = directory.getParentId().k() + "=?";
            whereArgs.add(parent.getId().v());
        }
        where += " and " + directory.getIsDirectory().k() + "=? and " + directory.getName().k() + "=?";
        whereArgs.add(1);
        whereArgs.add(directory.getName().v());
        List<FsDirectory> result = sqlQueries.load(directory.getAllAttributes(), directory, where, whereArgs);
        if (result.size() > 0) {
            return result.get(0);
        }
        return null;
    }

    public FsDirectory getSubDirectoryByName(Long parentId, String name) throws SqlQueriesException {
        FsDirectory directory = new FsDirectory();
        String where = "";
        List<Object> whereArgs = new ArrayList<>();
        where = directory.getParentId().k() + "=?";
        whereArgs.add(parentId);
        where += " and " + directory.getIsDirectory().k() + "=? and " + directory.getName().k() + "=?";
        whereArgs.add(1);
        whereArgs.add(name);
        List<FsDirectory> result = sqlQueries.load(directory.getAllAttributes(), directory, where, whereArgs);
        if (result.size() > 0) {
            return result.get(0);
        }
        return null;
    }

    public List<FsDirectory> getSubDirectoriesVersion(Long id, Long version) throws SqlQueriesException {
        FsDirectory dir = new FsDirectory();
        String where = dir.getParentId().k() + "=? and " + dir.getVersion().k() + ">?";
        List<Object> whereArgs = ISQLQueries.whereArgs(id, version);
        List<SQLTableObject> result = sqlQueries.load(dir.getAllAttributes(), dir, where, whereArgs);
        List<FsDirectory> dirs = result.stream().map(s -> (FsDirectory) s).collect(Collectors.toList());
        return dirs;
    }

    public List<FsEntry> getDirectoryContent(Long id) throws SqlQueriesException {
        GenericFSEntry fsEntry = new GenericFSEntry();
        String where = fsEntry.getParentId().k() + "=?";
        ArrayList<Object> whereArgs = new ArrayList<>();
        whereArgs.add(id);
        List<FsEntry> result = sqlQueries.load(fsEntry.getAllAttributes(), fsEntry, where, whereArgs);
        return result;
    }


    public FsDirectory getDirectoryById(Long id) throws SqlQueriesException {
        FsDirectory directory = new FsDirectory();
        List<Object> whereArgs = new ArrayList<>();
        String where = directory.getId().k() + "=?";
        if (id != null) {
            where = directory.getId().k() + "=?";
            whereArgs.add(id);
        } else {
            where = directory.getId().k() + " is null";
        }
        List<FsDirectory> result = sqlQueries.load(directory.getAllAttributes(), directory, where, whereArgs);
        if (result.size() == 1)
            return result.get(0);
        return null;
    }

    public Long getLatestVersion() throws SqlQueriesException {
        FsDirectory directory = new FsDirectory();
        String sql = "select max(" + directory.getVersion().k() + ") from " + directory.getTableName();
        Integer v = sqlQueries.queryValue(sql, Integer.class);
        if (v == null)
            return 0L;
        else
            return Long.valueOf(v.toString());
    }

    public void setDriveSettings(DriveSettings driveSettings) {
        this.driveSettings = driveSettings;
    }

    public List<GenericFSEntry> getDelta(long version) throws SqlQueriesException {
        GenericFSEntry fsEntry = new GenericFSEntry();
        String where = fsEntry.getVersion().k() + ">?";
        List<Object> args = new ArrayList<>();
        args.add(version);
        List<GenericFSEntry> result = sqlQueries.load(fsEntry.getAllAttributes(), fsEntry, where, args, null);
        return result;
    }

    public GenericFSEntry getGenericByINode(Long inode) throws SqlQueriesException {
        GenericFSEntry fsEntry = new GenericFSEntry();
        List<Object> args = new ArrayList<>();
        args.add(inode);
        List<GenericFSEntry> res = sqlQueries.load(fsEntry.getAllAttributes(), fsEntry, fsEntry.getiNode().k() + "=?", args);
        if (res.size() == 1)
            return res.get(0);
        else if (res.size() > 1)
            System.err.println("FsDao.getGenericFilesByINode.MORE:THAN:ONE");
        return null;

    }

    public FsDirectory getFsDirectoryByPath(File f) throws SqlQueriesException {
        RootDirectory rootDirectory = driveDatabaseManager.getDriveSettings().getRootDirectory();
        String rootPath = rootDirectory.getPath();
        String path = f.getAbsolutePath();
        String sh = path.substring(rootPath.length());
        FsDirectory parent = this.getRootDirectory();
        String[] parts = sh.split(File.separator);
        for (int i = 1; i < parts.length; i++) {
            String name = parts[i];
            if (parent == null)
                return null;
            parent = this.getSubDirectoryByName(parent.getId().v(), name);
        }
        return parent;
    }

    public FsDirectory getRootDirectory() throws SqlQueriesException {
        FsDirectory dummy = new FsDirectory();
        String where = dummy.getParentId().k() + " is null";
        List<FsDirectory> roots = sqlQueries.load(dummy.getAllAttributes(), dummy, where, null, null);
        assert roots.size() == 1;
        return roots.get(0);
    }

    public GenericFSEntry getGenericFileByName(GenericFSEntry genericFSEntry) throws SqlQueriesException {
        List<Object> args = new ArrayList<>();
        if (genericFSEntry.getParentId().v() != null)
            args.add(genericFSEntry.getParentId().v());
        args.add(genericFSEntry.getName().v());
        String where = genericFSEntry.getParentId().k() + " is null and " + genericFSEntry.getName().k() + "=?";
        if (genericFSEntry.getParentId().v() != null)
            where = genericFSEntry.getParentId().k() + "=? and " + genericFSEntry.getName().k() + "=?";
        List<GenericFSEntry> res = sqlQueries.load(genericFSEntry.getAllAttributes(), genericFSEntry, where, args);
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public boolean hasId(Long id) throws SqlQueriesException {
        GenericFSEntry dummy = new GenericFSEntry();
        String where = dummy.getId().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(id);
        return sqlQueries.load(dummy.getInsertAttributes(), dummy, where, args).size() > 0;
    }

    public void deleteById(Long fsId) throws SqlQueriesException {
        System.out.println("FsDao.deleteById: " + fsId);
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(fsId);
        FsEntry fsEntry = new GenericFSEntry();
        List<FsEntry> dirContent = this.getDirectoryContent(fsId);
        for (FsEntry content : dirContent)
            this.delete(content);
        sqlQueries.delete(fsEntry, fsEntry.getId().k() + "=?", whereArgs);
    }

    public void delete(FsEntry fsEntry) throws SqlQueriesException {
        deleteById(fsEntry.getId().v());
    }

    public void insertOrUpdate(FsEntry fsEntry) throws SqlQueriesException {
        if (fsEntry.getId().v() != null && hasId(fsEntry.getId().v())) {
            update(fsEntry);
        } else {
            insert(fsEntry);
        }
    }

    public List<GenericFSEntry> getContentByFsDirectory(Long fsId) throws SqlQueriesException {
        GenericFSEntry genericFSEntry = new GenericFSEntry();
        String where = "";
        List<Object> whereArgs = new ArrayList<>();
        if (fsId == null) {
            where = genericFSEntry.getParentId().k() + " is null";
        } else {
            where = genericFSEntry.getParentId().k() + "=?";
            whereArgs.add(fsId);
        }
        //where += " and " + genericFSEntry.getIsDirectory().k() + "=?";
        //whereArgs.add(1);

        List<GenericFSEntry> result = sqlQueries.load(genericFSEntry.getAllAttributes(), genericFSEntry, where, whereArgs);
        return result;
    }

    public File getFileByFsFile(RootDirectory rootDirectory, FsEntry fsEntry) throws SqlQueriesException {
        if (fsEntry.getParentId().v() == null)
            return new File(rootDirectory.getPath());
        Stack<FsDirectory> stack = new Stack<>();
        FsDirectory dbDir = this.getDirectoryById(fsEntry.getParentId().v());
        while (dbDir != null && dbDir.getParentId().v() != null) {
            stack.add(dbDir);
            dbDir = this.getDirectoryById(dbDir.getParentId().v());
        }
        String path = rootDirectory.getPath() + File.separator;
        while (!stack.empty()) {
            dbDir = stack.pop();
            path += dbDir.getName().v() + File.separator;
        }
        path += fsEntry.getName().v();
        return new File(path);

    }

    public FsDirectory getFsDirectoryById(Long fsId) throws SqlQueriesException {
        FsDirectory dummy = new FsDirectory();
        String where = dummy.getId().k() + "=?";
        List<FsDirectory> directories = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(fsId));
        if (directories.size() == 1)
            return directories.get(0);
        return null;
    }

    public GenericFSEntry getGenericById(Long fsId) throws SqlQueriesException {
        GenericFSEntry dummy = new GenericFSEntry();
        String where = dummy.getId().k() + "=?";
        List<GenericFSEntry> directories = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(fsId));
        if (directories.size() == 1)
            return directories.get(0);
        return null;
    }

    public List<FsFile> getNonSyncedFilesByHash(String hash) throws SqlQueriesException {
        FsFile dummy = new FsFile();
        String where = dummy.getContentHash().k() + "=? and " + dummy.getSynced().k() + "=?";
        List<FsFile> fsFiles = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(hash, false));
        return fsFiles;
    }

    public List<FsFile> getNonSyncedFilesByFsDirectory(Long fsId) throws SqlQueriesException {
        FsFile dummy = new FsFile();
        String where = dummy.getParentId().k() + "=? and " + dummy.getSynced().k() + "=? and " + dummy.getIsDirectory().k() + "=?";
        List<FsFile> fsFiles = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(fsId, false, false));
        return fsFiles;
    }

    public List<String> searchTransfer() throws SqlQueriesException {
        FsFile fsFile = new FsFile();
        TransferDetails transfer = new TransferDetails();
        String where = fsFile.getSynced().k() + "=? and exists ( select * from " + transfer.getTableName() + " t where t." + transfer.getHash().k() + "=" + fsFile.getContentHash().k() + ")";
        return sqlQueries.loadColumn(fsFile.getContentHash(), String.class, fsFile, where, ISQLQueries.whereArgs(true), null);
    }

    public void setSynced(Long id, boolean synced) throws SqlQueriesException {
        assert id != null;
        FsFile dummy = new FsFile();
        String statement = "update " + dummy.getTableName() + " set " + dummy.getSynced().k() + "=? where " + dummy.getId().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.whereArgs(true, id));
    }

    public ISQLResource<FsFile> getNonSyncedFilesResource() throws SqlQueriesException {
        FsFile fsFile = new FsFile();
        String where = fsFile.getSynced().k() + "=?";
        return sqlQueries.loadResource(fsFile.getAllAttributes(), FsFile.class, where, ISQLQueries.whereArgs(false));
    }
}


package de.mel.filesync.sql.dao;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.N;
import de.mel.auth.tools.RWSemaphore;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.filesync.sql.*;
import de.mel.sql.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class FsDao extends Dao {
    protected FsFile dummy = new FsFile();
    protected FsDirectory dir = new FsDirectory();
    protected String tableName = dummy.getTableName();

    public FsEntry getBottomFsEntry(Stack<IFile> fileStack) throws SqlQueriesException {
        if (fileStack.size() == 0) { //&& fileStack[0].length() == 0) {
            return getRootDirectory();
        }
        FsEntry bottomFsEntry = getRootDirectory();
        FsEntry lastFsEntry = null;
        do {
            Long parentId = (lastFsEntry != null) ? lastFsEntry.getId().v() : fileSyncSettings.getRootDirectory().getId();
            lastFsEntry = getGenericSubByName(parentId, fileStack.peek().getName());
            if (lastFsEntry != null) {
                bottomFsEntry = lastFsEntry;
                fileStack.pop();
            }
        } while (lastFsEntry != null && !fileStack.empty());
        return bottomFsEntry;
    }

    private int printLock(String method, AtomicInteger count) {
        int n = count.incrementAndGet();
        Lok.debug("FsDao." + method + "(" + n + ").on " + Thread.currentThread().getName());
        return n;
    }

    private final FileSyncDatabaseManager fileSyncDatabaseManager;
    private FileSyncSettings fileSyncSettings;

    public FsDao(FileSyncDatabaseManager fileSyncDatabaseManager, ISQLQueries ISQLQueries) {
        super(ISQLQueries);
        this.fileSyncDatabaseManager = fileSyncDatabaseManager;
    }


    public void update(FsEntry fsEntry) throws SqlQueriesException {
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(fsEntry.getId().v());
        sqlQueries.update(fsEntry, fsEntry.getId().k() + "=?", whereArgs);
    }

    public FsFile getFsFileByName(FsFile fsFile) throws SqlQueriesException {
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
        List<FsFile> tableObjects = sqlQueries.load(fsFile.getAllAttributes(), dummy, where, whereArguments);
        if (tableObjects.size() == 0) {
            return null;
        } else {
            return tableObjects.get(0);
        }
    }

    public FsFile getFsFileByName(Long dirId, String name) throws SqlQueriesException {
        FsFile dummy = this.dummy.newDummyInstance();
        dummy.getParentId().v(dirId);
        dummy.getName().v(name);
        return getFsFileByName(dummy);
    }

    public List<FsFile> getFilesByHash(String hash) throws SqlQueriesException {
        String where = dummy.getContentHash().k() + "=?";
        List<FsFile> fsFiles = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(hash));
        return fsFiles;
    }

    public List<FsFile> getSyncedFilesByHash(String hash) throws SqlQueriesException {
        String where = dummy.getContentHash().k() + "=? and " + dummy.getSynced().k() + "=?";
        List<FsFile> fsFiles = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(hash, true));
        return fsFiles;
    }

    public List<FsFile> getFilesByFsDirectory(Long id) throws SqlQueriesException {
        String where = "";
        List<Object> whereArguments = new ArrayList<>();
        if (id == null) {
            where = dummy.getParentId().k() + " is null";
        } else {
            where = dummy.getParentId().k() + "=?";
            whereArguments.add(id);
        }
        where += " and " + dummy.getIsDirectory().k() + "=?";
        whereArguments.add(0);
        List<FsFile> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, whereArguments);
        return result;
    }

    public void markFileMissed(FsFile f) throws SqlQueriesException {
        f.getVersion().v((Long) null);
        update(f);
    }


    public FsFile getFile(Long id) throws SqlQueriesException {
        String where = dummy.getId().k() + "=?";
        List<Object> whereArguments = new ArrayList<>();
        whereArguments.add(id);
        List<FsFile> tableObjects = sqlQueries.load(dummy.getAllAttributes(), dummy, where, whereArguments);
        if (tableObjects.size() == 0) {
            return null;
        } else {
            return tableObjects.get(0);
        }
    }


    public FsEntry insert(FsEntry fsEntry) throws SqlQueriesException {
        Long id;
        if (fsEntry.getId().notNull())
            id = sqlQueries.insertWithAttributes(fsEntry, fsEntry.getAllAttributes());
        else
            id = sqlQueries.insert(fsEntry);

        fsEntry.getId().v(id);
        return fsEntry;
    }

    public List<FsDirectory> getSubDirectoriesByParentId(Long id) throws SqlQueriesException {
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

    public FsDirectory getSubDirectoryByName(Long parentId, String name) throws SqlQueriesException {
        String where = "";
        List<Object> whereArgs = new ArrayList<>();
        where = dir.getParentId().k() + "=?";
        whereArgs.add(parentId);
        where += " and " + dir.getIsDirectory().k() + "=? and " + dir.getName().k() + "=?";
        whereArgs.add(1);
        whereArgs.add(name);
        List<FsDirectory> result = sqlQueries.load(dir.getAllAttributes(), dir, where, whereArgs);
        if (result.size() > 0) {
            return result.get(0);
        }
        return null;
    }

    public List<FsEntry> getDirectoryContent(Long id) throws SqlQueriesException {
        String where = dir.getParentId().k() + "=?";
        ArrayList<Object> whereArgs = new ArrayList<>();
        whereArgs.add(id);
        List<FsEntry> result = sqlQueries.load(dir.getAllAttributes(), dir, where, whereArgs);
        return result;
    }


    public FsDirectory getDirectoryById(Long id) throws SqlQueriesException {
        List<Object> whereArgs = new ArrayList<>();
        String where = dir.getId().k() + "=?";
        if (id != null) {
            where = dir.getId().k() + "=?";
            whereArgs.add(id);
        } else {
            where = dir.getId().k() + " is null";
        }
        List<FsDirectory> result = sqlQueries.load(dir.getAllAttributes(), dir, where, whereArgs);
        if (result.size() == 1)
            return result.get(0);
        return null;
    }

    public Long getLatestVersion() throws SqlQueriesException {
        String sql = "select max(" + dir.getVersion().k() + ") from " + dir.getTableName();
        Long v = sqlQueries.queryValue(sql, Long.class, null);
        if (v == null)
            return 0L;
        else
            return Long.valueOf(v.toString());
    }

    public void setFileSyncSettings(FileSyncSettings fileSyncSettings) {
        this.fileSyncSettings = fileSyncSettings;
    }

    public List<GenericFSEntry> getDelta(long version) throws SqlQueriesException {
        GenericFSEntry dummy = new GenericFSEntry();
        String where = dummy.getVersion().k() + ">?";
        List<Object> args = new ArrayList<>();
        args.add(version);
        List<GenericFSEntry> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args, null);
        return result;
    }

    public ISQLResource<GenericFSEntry> getDeltaResource(long version) throws SqlQueriesException {
        GenericFSEntry fsEntry = new GenericFSEntry();
        String where = fsEntry.getVersion().k() + ">?"; //">? order by " + fsEntry.getDepth().k() + "," + fsEntry.getIsDirectory().k();
        List<Object> args = new ArrayList<>();
        args.add(version);
        ISQLResource<GenericFSEntry> result = sqlQueries.loadResource(fsEntry.getAllAttributes(), GenericFSEntry.class, where, args);
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

    public FsDirectory getFsDirectoryByPath(IFile f) throws SqlQueriesException {
        try {
            RootDirectory rootDirectory = fileSyncDatabaseManager.getFileSyncSettings().getRootDirectory();
            String rootPath = rootDirectory.getPath();
            //todo Exception here
            if (!f.getAbsolutePath().startsWith(rootPath))
                return null;
            if (f.getAbsolutePath().length() == rootPath.length())
                return getRootDirectory();
            FsDirectory parent = this.getRootDirectory();
            Stack<IFile> fileStack = new Stack<>();
            IFile ff = f;
            while (ff.getAbsolutePath().length() > rootPath.length()) {
                fileStack.push(ff);
                ff = ff.getParentFile();
            }
            while (!fileStack.empty()) {
                String name = fileStack.pop().getName();
                if (parent == null)
                    return null;
                parent = this.getSubDirectoryByName(parent.getId().v(), name);
            }
            return parent;
        } catch (StringIndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return null;
    }

    public FsDirectory getRootDirectory() throws SqlQueriesException {
        String where = dir.getParentId().k() + " is null";
        List<FsDirectory> roots = sqlQueries.load(dir.getAllAttributes(), dir, where, null, null);
        assert roots.size() == 1;
        return roots.get(0);
    }

    public GenericFSEntry getGenericChildByName(long parentId, String name) throws SqlQueriesException {
        GenericFSEntry dummy = new GenericFSEntry();
        dummy.getParentId().v(parentId);
        dummy.getName().v(name);
        return getGenericFileByName(dummy);
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
        String where = dummy.getId().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(id);
        return sqlQueries.load(dummy.getInsertAttributes(), dummy, where, args).size() > 0;
    }

    public void deleteById(Long fsId) throws SqlQueriesException {
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(fsId);
        List<FsEntry> dirContent = this.getDirectoryContent(fsId);
        for (FsEntry content : dirContent)
            this.delete(content);
        sqlQueries.delete(dummy, dummy.getId().k() + "=?", whereArgs);
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

    public IFile getFileByFsFile(RootDirectory rootDirectory, FsEntry fsEntry) throws SqlQueriesException {
        if (fsEntry.getParentId().v() == null)
            return AbstractFile.instance(rootDirectory.getPath());
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
        return AbstractFile.instance(path);

    }

    public FsDirectory getFsDirectoryById(Long fsId) throws SqlQueriesException {
        String where = dir.getId().k() + "=?";
        List<FsDirectory> directories = sqlQueries.load(dir.getAllAttributes(), dir, where, ISQLQueries.args(fsId));
        if (directories.size() == 1)
            return directories.get(0);
        return null;
    }

    public GenericFSEntry getGenericById(Long fsId) throws SqlQueriesException {
        GenericFSEntry dummy = new GenericFSEntry();
        String where = dummy.getId().k() + "=?";
        List<GenericFSEntry> directories = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(fsId));
        if (directories.size() == 1)
            return directories.get(0);
        return null;
    }

    public List<FsFile> getNonSyncedFilesByHash(String hash) throws SqlQueriesException {
        String where = dummy.getContentHash().k() + "=? and " + dummy.getSynced().k() + "=?";
        List<FsFile> fsFiles = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(hash, false));
        //return new HashSet<>(fsFiles);
        return fsFiles;
    }

    public List<FsFile> getNonSyncedFilesByFsDirectory(Long fsId) throws SqlQueriesException {
        String where = dummy.getParentId().k() + "=? and " + dummy.getSynced().k() + "=? and " + dummy.getIsDirectory().k() + "=?";
        List<FsFile> fsFiles = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(fsId, false, false));
        return fsFiles;
    }

    /**
     * searches for all hashes that the {@link de.mel.filesync.transfer.TManager} is looking for and are already in the share
     *
     * @return
     * @throws SqlQueriesException
     */
    public List<String> searchTransfer() throws SqlQueriesException {
        DbTransferDetails t = new DbTransferDetails();
        String query = "select f." + dummy.getContentHash().k() + " from " + t.getTableName() + " t left join " + dummy.getTableName()
                + " f on f." + dummy.getContentHash().k() + "=t." + t.getHash().k() + " where f." + dummy.getSynced().k() + "=?";
        return sqlQueries.loadColumn(dummy.getContentHash(), String.class, query, ISQLQueries.args(true));
    }

    public void setSynced(Long id, boolean synced) throws SqlQueriesException {
        assert id != null;
        String statement = "update " + dummy.getTableName() + " set " + dummy.getSynced().k() + "=? where " + dummy.getId().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.args(synced, id));
    }

    public GenericFSEntry getGenericSubByName(Long parentId, String name) throws SqlQueriesException {
        GenericFSEntry genericFSEntry = new GenericFSEntry();
        String where = genericFSEntry.getParentId().k() + " =? and " + genericFSEntry.getName().k() + "=?";
        List<GenericFSEntry> gens = sqlQueries.load(genericFSEntry.getAllAttributes(), genericFSEntry, where, ISQLQueries.args(parentId, name));
        if (gens.size() == 1)
            return gens.get(0);
        return null;
    }

    public FsFile getFsFileByFile(File file) throws SqlQueriesException {
        RootDirectory rootDirectory = fileSyncDatabaseManager.getFileSyncSettings().getRootDirectory();
        String rootPath = rootDirectory.getPath();
        //todo throw Exception if f is not in rootDirectory
        if (file.getAbsolutePath().length() < rootPath.length())
            return null;
        File ff = new File(file.getAbsolutePath());
        Stack<File> fileStack = new Stack<>();
        while (ff.getAbsolutePath().length() > rootPath.length()) {
            fileStack.push(ff);
            ff = ff.getParentFile();
        }
        FsEntry lastEntry = this.getRootDirectory();
        while (!fileStack.empty()) {
            if (lastEntry == null) {
                Lok.debug("FsDao.getFsFileByFile.did not find");
                return null;
            }
            String name = fileStack.pop().getName();
            lastEntry = this.getGenericSubByName(lastEntry.getId().v(), name);
        }
        return (FsFile) lastEntry.copyInstance();
    }

    public boolean desiresHash(String hash) throws SqlQueriesException {
        String query = "select count(*)>0 from " + dummy.getTableName() + " where " + dummy.getSynced().k() + "=? and " + dummy.getContentHash().k() + "=?";
        Integer result = sqlQueries.queryValue(query, Integer.class, ISQLQueries.args(false, hash));
        return SqliteConverter.intToBoolean(result);
    }

    public Long countDirectories() {
        return N.result(() -> sqlQueries.queryValue("select count(*) from " + dir.getTableName()
                        + " where " + dir.getIsDirectory().k() + "=?", Long.class, ISQLQueries.args(true))
                , 0L);
    }

    public ISQLResource<GenericFSEntry> all() throws SqlQueriesException {
        return sqlQueries.loadResource(dummy.getAllAttributes(), GenericFSEntry.class, null, null);
    }

    public void updateName(long id, @NotNull String name) throws SqlQueriesException {
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getName().k() + "=? where " + dummy.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.args(name, id));
    }
}

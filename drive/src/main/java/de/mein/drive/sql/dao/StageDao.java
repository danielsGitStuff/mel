package de.mein.drive.sql.dao;

import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.sql.*;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by xor on 11/20/16.
 */
@SuppressWarnings("Duplicates")
public class StageDao extends Dao.LockingDao {
    private final FsDao fsDao;
    private DriveDatabaseManager driveDatabaseManager;

    public StageDao(DriveDatabaseManager driveDatabaseManager, ISQLQueries isqlQueries, FsDao fsDao) {
        super(isqlQueries);
        this.fsDao = fsDao;
        this.driveDatabaseManager = driveDatabaseManager;

    }
/*
    public StageDao(SQLQueries sqlQueries, boolean lock) {
        super(sqlQueries, lock);
    }*/

    /**
     * finds the relating stage. starts searching from leParentDirectory and traverses Stage table till it finds it.
     *
     * @param f
     * @return relating Stage or null if f is not staged
     */
    public Stage getStageByPath(Long stageSetId, File f) throws SqlQueriesException {
        RootDirectory rootDirectory = driveDatabaseManager.getDriveSettings().getRootDirectory();
        String rootPath = rootDirectory.getPath();
        String path = f.getAbsolutePath();
        // skip a File.separator
        String sh = path.substring(rootPath.length());
        if (sh.startsWith("/"))
            sh = sh.substring(1);
        //Stage parentStage = getStageByFsId(rootDirectory.getId(), stageSetId);
        String[] parts = sh.split(File.separator);
        BottomDirAndPath bottomDirAndPath = getBottomFsDir(parts);
        Stage bottomStage = this.getStageByFsId(bottomDirAndPath.fsDirectory.getId().v(), stageSetId);
        for (int i = 0; i < bottomDirAndPath.parts.length; i++) {
            String name = bottomDirAndPath.parts[i];
            if (bottomStage == null)
                continue;
            bottomStage = this.getSubStageByNameAndParent(stageSetId, bottomStage.getId(), name);
        }
        return bottomStage;
    }

    public StageSet getStageSetById(Long stageSetId) throws SqlQueriesException {
        StageSet dummy = new StageSet();
        List<StageSet> result = sqlQueries.load(dummy.getAllAttributes(), dummy, dummy.getId().k() + "=?", ISQLQueries.whereArgs(stageSetId));
        if (result.size() == 1)
            return result.get(0);
        return null;
    }

    public void deleteStageById(Long id) throws SqlQueriesException {
        Stage dummy = new Stage();
        sqlQueries.delete(dummy, dummy.getIdPair().k() + "=?", ISQLQueries.whereArgs(id));
    }

    public boolean stageSetHasContent(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String query = "select count(*) from " + dummy.getTableName() + " where " + dummy.getStageSetPair().k() + "=?";
        Integer res = sqlQueries.queryValue(query, Integer.class, ISQLQueries.whereArgs(stageSetId));
        return res > 0;
    }

    public void updateStageSet(StageSet stageSet) throws SqlQueriesException {
        sqlQueries.update(stageSet, stageSet.getId().k() + "=?", ISQLQueries.whereArgs(stageSet.getId().v()));
    }

    public List<StageSet> getStagedStageSetsFromFS() throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        String where = stageSet.getType().k() + "=? and " + stageSet.getStatus().k() + "=?";
        return sqlQueries.load(stageSet.getAllAttributes(), stageSet, where, ISQLQueries.whereArgs(DriveStrings.STAGESET_TYPE_FS, DriveStrings.STAGESET_STATUS_STAGED));
    }

    public List<StageSet> getStagedStageSetsFromServer() throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        String where = stageSet.getType().k() + "=? and " + stageSet.getStatus().k() + "=?";
        return sqlQueries.load(stageSet.getAllAttributes(), stageSet, where, ISQLQueries.whereArgs(DriveStrings.STAGESET_TYPE_FROM_SERVER, DriveStrings.STAGESET_STATUS_STAGED));
    }

    public ISQLResource<Stage> getStagesResource(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getStageSetPair().k() + "=?";
        return sqlQueries.loadResource(stage.getAllAttributes(), Stage.class, where, ISQLQueries.whereArgs(stageSetId));
    }

    public File getFileByStage(RootDirectory rootDirectory, Stage stage) throws SqlQueriesException {
        final Long stageSetId = stage.getId();
        Stack<Stage> stageStack = new Stack<>();
        stageStack.push(stage);
        while (stage.getFsId() != null) {
            stage = getStageById(stage.getParentId());
            stageStack.push(stage);
        }
        FsEntry fsEntry = fsDao.getGenericById(stageStack.pop().getFsId());
        File file = fsDao.getFileByFsFile(rootDirectory, fsEntry);
        StringBuilder path = new StringBuilder(file.getAbsolutePath());
        while (!stageStack.empty()) {
            path.append(File.separator).append(stageStack.pop().getName());
        }
        return new File(path.toString());
    }


    public static class BottomDirAndPath {
        private String[] parts;
        private FsDirectory fsDirectory;

        public FsDirectory getFsDirectory() {
            return fsDirectory;
        }

        public String[] getParts() {
            return parts;
        }

        public BottomDirAndPath setFsDirectory(FsDirectory fsDirectory) {
            this.fsDirectory = fsDirectory;
            return this;
        }

        public BottomDirAndPath setParts(String[] parts) {
            this.parts = parts;
            return this;
        }
    }

    private BottomDirAndPath getBottomFsDir(String[] parts) throws SqlQueriesException {
        BottomDirAndPath result = new BottomDirAndPath();
        if (parts.length == 1 && parts[0].length() == 0) {
            result.fsDirectory = driveDatabaseManager.getFsDao().getRootDirectory();
            result.parts = new String[0];
            return result;
        }
        FsDirectory bottomFsDir = driveDatabaseManager.getFsDao().getRootDirectory();
        FsEntry lastFsDir = null;
        int offset = 0;
        do {
            Long parentId = (lastFsDir != null) ? lastFsDir.getId().v() : driveDatabaseManager.getDriveSettings().getRootDirectory().getId();
            lastFsDir = driveDatabaseManager.getFsDao().getSubDirectoryByName(parentId, parts[offset]);
            if (lastFsDir != null) {
                bottomFsDir = (FsDirectory) lastFsDir;
                offset++;
            }
        } while (offset < parts.length && lastFsDir != null);
        //copy leftovers
        int left = parts.length - offset;
        String[] leftover = new String[0];
        if (left > 0)
            leftover = new String[parts.length - offset];
        for (int ii = offset; ii < parts.length; ii++) {
            leftover[ii - offset] = parts[ii];
        }
        result.fsDirectory = bottomFsDir;
        result.parts = leftover;
        return result;
    }

    private Stage getSubStageByNameAndFsParent(Stage parentStage, String name) throws SqlQueriesException {
        String where = parentStage.getFsParentIdPair().k() + "=? and " + parentStage.getNamePair().k() + "=? and " + parentStage.getStageSetPair().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(parentStage.getId());
        args.add(name);
        args.add(parentStage.getStageSet());
        List<Stage> res = sqlQueries.load(parentStage.getAllAttributes(), parentStage, where, args);
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    private Stage getSubStageByNameAndParent(Long stageSetId, Long parentId, String name) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getParentIdPair().k() + "=? and " + dummy.getNamePair().k() + "=? and " + dummy.getStageSetPair().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(parentId);
        args.add(name);
        args.add(stageSetId);
        List<Stage> res = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public Stage getStageByFsId(Long id, Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getFsIdPair().k() + "=? and " + dummy.getStageSetPair().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(id);
        args.add(stageSetId);
        List<Stage> res = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public Stage insert(Stage stage) throws SqlQueriesException {
        Long id = sqlQueries.insert(stage);
        return stage.setId(id);
    }

    public ISQLResource<Stage> getStagesByStageSet(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? order by " + dummy.getIdPair().k() + " asc";
        return sqlQueries.loadResource(dummy.getAllAttributes(), Stage.class, where, ISQLQueries.whereArgs(stageSetId));
    }

    public List<Stage> getStagesByStageSetAsList(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? order by " + dummy.getIdPair().k() + " asc";
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(stageSetId));
    }

    public Stage getStageById(Long id) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getIdPair().k() + "=?";
        List<Stage> res = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(id));
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public StageSet createStageSet(String type, Long originCertId, String originServiceUuid) throws SqlQueriesException {
        StageSet stageSet = new StageSet().setType(type).setOriginCertId(originCertId)
                .setOriginServiceUuid(originServiceUuid).setStatus(DriveStrings.STAGESET_STATUS_STAGING);
        Long id = sqlQueries.insert(stageSet);
        return stageSet.setId(id);
    }

    public void deleteStageSet(Long id) throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        List<Object> args = new ArrayList<>();
        args.add(id);
        sqlQueries.delete(stageSet, stageSet.getId().k() + "=?", args);
    }

    public Stage getStageByStageSetParentName(Long stageSetId, Long parentId, String name) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getParentIdPair().k() + "=? and " + dummy.getNamePair().k() + "=? and " + dummy.getStageSetPair().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(parentId);
        args.add(name);
        args.add(stageSetId);
        List<Stage> res = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public void update(Stage stage) throws SqlQueriesException {
        String where = stage.getIdPair().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(stage.getId());
        sqlQueries.update(stage, where, args);
    }


    public FsEntry stage2FsEntry(Stage stage, long version) throws SqlQueriesException {
        FsDao fsDao = driveDatabaseManager.getFsDao();
        FsEntry fsEntry;
        if (stage.getIsDirectory()) {
            FsDirectory fsDirectory = fsDao.getDirectoryById(stage.getFsId());
            if (fsDirectory == null) {
                fsDirectory = new FsDirectory();
            }
            fsDirectory.getContentHash().v(stage.getContentHash());
            fsDirectory.getVersion().v(version);
            fsEntry = fsDirectory;
        } else {
            FsFile fsFile = fsDao.getFile(stage.getFsId());
            if (fsFile == null) {
                fsFile = new FsFile();
            }
            fsFile.getContentHash().v(stage.getContentHash());
            fsFile.getVersion().v(version);
            fsFile.getSize().v(stage.getSize());
            fsEntry = fsFile;
        }
        fsEntry.getParentId().v(stage.getFsParentId());
        fsEntry.getId().v(stage.getFsId());
        fsEntry.getName().v(stage.getName());
        //todo rer5
        fsEntry.getiNode().v(stage.getiNode());
        fsEntry.getModified().v(stage.getModified());
        return fsEntry;
    }

    public List<Stage> getDirectoriesByStageSet(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? and " + dummy.getIsDirectoryPair().k() + "=? order by " + dummy.getIdPair().k() + " asc";
        List<Object> args = new ArrayList<>();
        args.add(stageSetId);
        args.add(true);
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
    }

    public List<Stage> getSubStagesByFsDirectoryId(Long fsId, Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? and " + dummy.getFsParentIdPair().k() + "=? order by " + dummy.getIdPair().k() + " asc";
        List<Object> args = new ArrayList<>();
        args.add(stageSetId);
        args.add(fsId);
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
    }

    public List<Stage> getStageContent(Long stageId, Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? and " + dummy.getIdPair().k() + "=? order by " + dummy.getIdPair().k() + " asc";
        List<Object> args = new ArrayList<>();
        args.add(stageSetId);
        args.add(stageId);
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
    }

    public ISQLResource<de.mein.drive.sql.Stage> getFilesAsResource(Long stageSetId) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        Stage stage = new Stage();
        List<Object> args = new ArrayList<>();
        args.add(stageSetId);
        args.add(false);
        String where = stage.getStageSetPair().k() + "=? and " + stage.getIsDirectoryPair().k() + "=?";
        return sqlQueries.loadResource(stage.getAllAttributes(), Stage.class, where, args);
    }
}

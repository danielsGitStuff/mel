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
public class
StageDao extends Dao.LockingDao {
    private final FsDao fsDao;
    private DriveDatabaseManager driveDatabaseManager;

    public StageDao(DriveDatabaseManager driveDatabaseManager, ISQLQueries isqlQueries, FsDao fsDao) {
        super(isqlQueries);
        this.fsDao = fsDao;
        this.driveDatabaseManager = driveDatabaseManager;

    }

    /**
     * finds the relating stage. starts searching from leParentDirectory and traverses Stage table till it finds it.
     *
     * @param f
     * @return relating Stage or null if f is not staged
     */
    public Stage getStageByPath(Long stageSetId, File f) throws SqlQueriesException {
        RootDirectory rootDirectory = driveDatabaseManager.getDriveSettings().getRootDirectory();
        String rootPath = rootDirectory.getPath();
        //todo throw Exception if f is not in rootDirectory
        if (f.getAbsolutePath().length() < rootPath.length())
            return null;
        File ff = new File(f.getAbsolutePath());
        Stack<File> fileStack = new Stack<>();
        while (ff.getAbsolutePath().length() > rootPath.length()) {
            fileStack.push(ff);
            ff = ff.getParentFile();
        }

        FsEntry bottomFsEntry = getBottomFsEntry(fileStack);
        Stage bottomStage = this.getStageByFsId(bottomFsEntry.getId().v(), stageSetId);
        while (!fileStack.empty()) {
            String name = fileStack.pop().getName();
            if (bottomStage == null)
                continue;
            bottomStage = this.getSubStageByNameAndParent(stageSetId, bottomStage.getId(), name);
        }
        return bottomStage;
    }

    private FsEntry getBottomFsEntry(Stack<File> fileStack) throws SqlQueriesException {
        if (fileStack.size() == 0) { //&& fileStack[0].length() == 0) {
            return driveDatabaseManager.getFsDao().getRootDirectory();
        }
        FsEntry bottomFsEntry = driveDatabaseManager.getFsDao().getRootDirectory();
        FsEntry lastFsEntry = null;
        do {
            Long parentId = (lastFsEntry != null) ? lastFsEntry.getId().v() : driveDatabaseManager.getDriveSettings().getRootDirectory().getId();
            lastFsEntry = driveDatabaseManager.getFsDao().getGenericSubByName(parentId, fileStack.peek().getName());
            if (lastFsEntry != null) {
                bottomFsEntry = lastFsEntry;
                fileStack.pop();
            }
        } while (lastFsEntry != null && !fileStack.empty());
        return bottomFsEntry;
    }

    public StageSet getStageSetById(Long stageSetId) throws SqlQueriesException {
        StageSet dummy = new StageSet();
        List<StageSet> result = sqlQueries.load(dummy.getAllAttributes(), dummy, dummy.getId().k() + "=?", ISQLQueries.whereArgs(stageSetId));
        if (result.size() == 1)
            return result.get(0);
        return null;
    }

    public void deleteStageById(Long id) throws SqlQueriesException {
        //todo debug
        Stage s = this.getStageById(id);
        if (s.getName().equals("samesub1.txt"))
            System.out.println("StageDao.deleteStageById.debugj0j3f034hh");
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
        String where = stageSet.getSource().k() + "=? and " + stageSet.getStatus().k() + "=?";
        return sqlQueries.load(stageSet.getAllAttributes(), stageSet, where, ISQLQueries.whereArgs(DriveStrings.STAGESET_TYPE_FS, DriveStrings.STAGESET_STATUS_STAGED));
    }

    public List<StageSet> getUpdateStageSetsFromServer() throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        String where = stageSet.getSource().k() + "=? and " + stageSet.getStatus().k() + "=?";
        return sqlQueries.load(stageSet.getAllAttributes(), stageSet, where, ISQLQueries.whereArgs(DriveStrings.STAGESET_TYPE_STAGING_FROM_SERVER, DriveStrings.STAGESET_STATUS_STAGED));
    }

    /**
     * @param stageSetId
     * @return SQLResource ordered by "created" timestamp
     * @throws SqlQueriesException
     */
    public ISQLResource<Stage> getStagesResource(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getStageSetPair().k() + "=? order by " + stage.getOrderPair().k();
        return sqlQueries.loadResource(stage.getAllAttributes(), Stage.class, where, ISQLQueries.whereArgs(stageSetId));
    }

    /**
     * @param stageSetId
     * @return SQLResource ordered by "created" timestamp
     * @throws SqlQueriesException
     */
    public ISQLResource<Stage> getNotMergedStagesResource(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getStageSetPair().k() + "=? and " + stage.getMergedPair().k() + "=? order by " + stage.getOrderPair().k();
        return sqlQueries.loadResource(stage.getAllAttributes(), Stage.class, where, ISQLQueries.whereArgs(stageSetId, false));
    }

    public File getFileByStage(Stage stage) throws SqlQueriesException {
        if (stage.getName().equals("samesub1.txt")) // todo debug
            System.err.println("StageDao.getFileByStage.debug 34234234");
        RootDirectory rootDirectory = driveDatabaseManager.getDriveSettings().getRootDirectory();
        final Long stageSetId = stage.getStageSet();
        Stack<Stage> stageStack = new Stack<>();
        FsEntry bottomFsEntry = null;
        while (stage != null) {
            if (stage.getFsId() != null) {
                bottomFsEntry = fsDao.getGenericById(stage.getFsId());
                if (bottomFsEntry != null)
                    break;
            }
            if (stage.getFsParentId() != null) {
                bottomFsEntry = fsDao.getGenericById(stage.getFsParentId());
                if (bottomFsEntry != null) {
                    stageStack.push(stage);
                    break;
                }
            }
            if (stage.getParentId() != null) {
                if (stage != null)
                    stageStack.push(stage);
                stage = getStageById(stage.getParentId());
            }
        }
        try {
            fsDao.getFileByFsFile(rootDirectory, bottomFsEntry);
        } catch (Exception e) {
            System.err.println("debug93h349");
        }
        File file = fsDao.getFileByFsFile(rootDirectory, bottomFsEntry);
        StringBuilder path = new StringBuilder(file.getAbsolutePath());
        while (!stageStack.empty()) {
            path.append(File.separator).append(stageStack.pop().getName());
        }
        return new File(path.toString());
    }

    public void deleteServerStageSets() throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        String where = stageSet.getSource().k() + "=? and " + stageSet.getStatus().k() + "=?";
        sqlQueries.delete(stageSet, where, ISQLQueries.whereArgs(DriveStrings.STAGESET_TYPE_STAGING_FROM_SERVER, DriveStrings.STAGESET_STATUS_STAGED));
    }

    public Stage getNotFlaggedStage(long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getStageSetPair().k() + "=? and " + stage.getMergedPair().k() + "=? order by " + stage.getOrderPair().k() + " limit 1";
        List<Stage> stages = sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.whereArgs(stageSetId, false));
        if (stages.size() > 0)
            return stages.get(0);
        return null;
    }

    public List<Stage> getDeletedDirectories(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getIsDirectoryPair().k() + "=? and " + stage.getStageSetPair().k() + "=? and " + stage.getDeletedPair().k() + "=?";
        return sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.whereArgs(true, stageSetId, true));
    }

    public Stage getStartStage(Long lStageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getIsDirectoryPair().k() + "=? and " + stage.getStageSetPair().k() + "=? and " + stage.getFsParentIdPair().k() + " is null limit 1";
        List<Stage> stages = sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.whereArgs(true, lStageSetId));
        if (stages.size() > 0)
            return stages.get(0);
        return null;
    }

    public Stage getLatestStageFromFsByINode(Long inode) throws SqlQueriesException {
        Stage stage = new Stage();
        StageSet set = new StageSet();
        String where = stage.getiNodePair().k() + "=? and " + stage.getStageSetPair().k()
                + "=(select " + set.getId().k() + " from " + set.getTableName() + " where " + set.getSource().k() + "=? order by "
                + set.getCreated().k() + " desc limit 1)";
        List<Stage> stages = sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.whereArgs(inode, DriveStrings.STAGESET_TYPE_FS));
        if (stages.size() > 0)
            return stages.get(0);
        return null;
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
        try {
            Long id = sqlQueries.insert(stage);
            return stage.setId(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
        return createStageSet(type, DriveStrings.STAGESET_STATUS_STAGING, originCertId, originServiceUuid);
    }

    public StageSet createStageSet(String type, String status, Long originCertId, String originServiceUuid) throws SqlQueriesException {
        StageSet stageSet = new StageSet().setSource(type).setOriginCertId(originCertId)
                .setOriginServiceUuid(originServiceUuid).setStatus(status);
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
        if (stage.getName().equals("samedir")) //todo debug
            System.err.println("StageDao.getFileByStage.debug h99g359");
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
        String where = dummy.getStageSetPair().k() + "=? and " + dummy.getParentIdPair().k() + "=? order by " + dummy.getIdPair().k() + " asc";
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

    public void flagMerged(Long stageId, Boolean found) throws SqlQueriesException {
        Stage stage = new Stage();
        String statement = "update " + stage.getTableName() + " set " + stage.getMergedPair().k() + "=? " +
                "where " + stage.getIdPair().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.whereArgs(found, stageId));
    }

}

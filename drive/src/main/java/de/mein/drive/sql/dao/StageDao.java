package de.mein.drive.sql.dao;

import de.mein.auth.tools.Eva;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.index.watchdog.StageIndexerRunnable;
import de.mein.drive.nio.FileTools;
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
    private final DriveSettings driveSettings;

    public StageDao(DriveSettings driveSettings, ISQLQueries isqlQueries, FsDao fsDao) {
        super(isqlQueries);
        this.fsDao = fsDao;
        this.driveSettings = driveSettings;
    }

    /**
     * finds the relating stage. starts searching from leParentDirectory and traverses Stage table till it finds it.
     *
     * @param f
     * @return relating Stage or null if f is not staged
     */
    public Stage getStageByPath(Long stageSetId, File f) throws SqlQueriesException {
        //todo debug
        if (f.getName().equals("samesub") && Thread.currentThread().getName().startsWith("StageIndexerRunnable for MeinDriveClient"))
            System.out.println("StageDao.getStageByPath.debug");
        RootDirectory rootDirectory = driveSettings.getRootDirectory();
        String rootPath = rootDirectory.getPath();
        //todo throw Exception if f is not in rootDirectory
        if (f.getAbsolutePath().length() < rootPath.length())
            return null;
        File ff = new File(f.getAbsolutePath());
        Stack<File> fileStack = FileTools.getFileStack(rootDirectory, ff);
        FsEntry bottomFsEntry = fsDao.getBottomFsEntry(fileStack);
        Stage bottomStage = this.getStageByFsId(bottomFsEntry.getId().v(), stageSetId);
        while (!fileStack.empty()) {
            String name = fileStack.pop().getName();
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
        return sqlQueries.load(stageSet.getAllAttributes(), stageSet, where, ISQLQueries.whereArgs(DriveStrings.STAGESET_SOURCE_FS, DriveStrings.STAGESET_STATUS_STAGED));
    }

    public List<StageSet> getUpdateStageSetsFromServer() throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        String where = stageSet.getSource().k() + "=? and " + stageSet.getStatus().k() + "=?";
        return sqlQueries.load(stageSet.getAllAttributes(), stageSet, where, ISQLQueries.whereArgs(DriveStrings.STAGESET_SOURCE_SERVER, DriveStrings.STAGESET_STATUS_STAGED));
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
        RootDirectory rootDirectory = driveSettings.getRootDirectory();
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
        sqlQueries.delete(stageSet, where, ISQLQueries.whereArgs(DriveStrings.STAGESET_SOURCE_SERVER, DriveStrings.STAGESET_STATUS_STAGED));
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
        List<Stage> stages = sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.whereArgs(inode, DriveStrings.STAGESET_SOURCE_FS));
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
            //todo debug
            if (stage.getName() == null)
                System.out.println("StageDao.insert.debugfj34ßf");
            if (stage.getName().equals("samesub1.txt") && stage.getSynced() != null && stage.getSynced())
                System.out.println("StageDao.insert.debugnj34ßfg3w");
            if (stage.getStageSet() == 6 && stage.getName().equals("samesub"))
                System.out.println("StageDao.insert.debugkjg093j0");
            if (stage.getName().equals("same2.txt") && stage.getParentId() == null)
                System.out.println("StageDao.insert.debug.1");
            if (!stage.getName().equals("[root]") && stage.getParentId() == null && stage.getFsId() == null)
                System.out.println("StageDao.insert.debugnu4hg0");
            Long id = sqlQueries.insert(stage);
            if (id == 92)
                System.out.println("StageDao.insert.debugbtrgm0rg");
            if (id == 94)
                System.out.println("StageDao.insert.debugfkß34");
            if (id == 95)
                System.out.println("StageDao.insert.debugjtu76uf");
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

    public List<Stage> getStagesByStageSetForCommit(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? and not(" + dummy.getFsIdPair().k() + " is null and " + dummy.getDeletedPair().k() + "=?) order by " + dummy.getIdPair().k() + " asc";
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(true, stageSetId));
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

    public StageSet createStageSet(String type, Long originCertId, String originServiceUuid, Long version) throws SqlQueriesException {
        return createStageSet(type, DriveStrings.STAGESET_STATUS_STAGING, originCertId, originServiceUuid, version);
    }

    public StageSet createStageSet(String type, String status, Long originCertId, String originServiceUuid, Long version) throws SqlQueriesException {
        StageSet stageSet = new StageSet().setSource(type).setOriginCertId(originCertId)
                .setOriginServiceUuid(originServiceUuid).setStatus(status).setVersion(version);
        Eva.eva((eva, count) -> {
            System.out.println("StageDao.createStageSet.eva." + count);
            if (count == 9)
                System.out.println("StageDao.createStageSet.fmner9hg0sa");
        });
        Long id = sqlQueries.insert(stageSet);
        //todo debug
        if (id == 6)
            System.out.println("StageDao.createStageSet.debug1");
        if (id == 4 && Thread.currentThread().getName().startsWith(StageIndexerRunnable.class.getSimpleName() + " for MeinDriveServer"))
            System.out.println("StageDao.createStageSet.debug2");
        if (id == 3)
            System.out.println("StageDao.createStageSet.debug2.2");
        if (id.toString().equals("7") && Thread.currentThread().getName().startsWith("MeinDriveClientService"))
            System.out.println("StageDao.createStageSet.debug3");
        if (id.toString().equals("3") && Thread.currentThread().getName().toLowerCase().contains("client"))
            System.out.println("StageDao.createStageSet.debug3");
        if (id.toString().equals("6") && Thread.currentThread().getName().toLowerCase().contains("client"))
            System.out.println("StageDao.createStageSet.debug4");
        if (id.toString().equals("5") && Thread.currentThread().getName().toLowerCase().contains("client"))
            System.out.println("StageDao.createStageSet.debug5");
        if (id == 2)
            System.out.println("StageDao.createStageSet.debug6");
        return stageSet.setId(id);
    }

    public void deleteStageSet(Long id) throws SqlQueriesException {
        //todo debug
        if (id == 2)
            System.out.println("StageDao.deleteStageSet.debugh98g4h5g");
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

    public void markRemoved(Long id) throws SqlQueriesException {
        Stage stage = new Stage();
        String statement = "update " + stage.getTableName() + " set " + stage.getRemovePair().k() + "=? where " + stage.getIdPair().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.whereArgs(true, id));
    }

    public void update(Stage stage) throws SqlQueriesException {
        if (!stage.getName().equals("[root]") && stage.getParentId() == null && stage.getFsId() == null)
            System.out.println("StageDao.update.debug.1");
        if (stage.getId() == null)
            System.out.println("StageDao.update.debug.2");
        if (stage.getId() == 63)
            System.out.println("StageDao.update.debug.3");
        String where = stage.getIdPair().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(stage.getId());
        sqlQueries.update(stage, where, args);
    }


    public FsEntry stage2FsEntry(Stage stage, long version) throws SqlQueriesException {
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
            fsFile.getSynced().v(stage.getSynced());
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

    public List<Stage> getStageContent(Long stageId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getParentIdPair().k() + "=? order by " + dummy.getOrderPair().k() + " asc";
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(stageId));
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

    public void deleteMarkedForRemoval(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String statement = "delete from " + stage.getTableName() + " where " + stage.getStageSetPair().k() + "=? and " + stage.getRemovePair().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.whereArgs(stageSetId, true));
    }

    public void updateInodeAndModifiedAndSynced(Long id, Long iNode, Long modified, Boolean synced) throws SqlQueriesException {
        Stage stage = new Stage();
        String statement = "update " + stage.getTableName() + " set " + stage.getiNodePair().k() + "=?, "
                + stage.getModifiedPair().k() + "=?, "
                + stage.getSyncedPair().k() + "=? "
                + "where " + stage.getIdPair().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.whereArgs(iNode, modified, synced, id));
    }

    public void flagMergedStageSet(Long stageSetId, boolean merged) throws SqlQueriesException {
        Stage stage = new Stage();
        String stmt = "update " + stage.getTableName() + " set " + stage.getMergedPair().k() + "=? where " + stage.getStageSetPair().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(merged, stageSetId));
    }

    public void flagSynced(Long id, boolean synced) throws SqlQueriesException {
        Stage stage = new Stage();
        String stmt = "update " + stage.getTableName() + " set " + stage.getSyncedPair().k() + "=? where " + stage.getIdPair().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(synced, id));
    }

    public Long getLatestStageSetVersion() throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        String query = "select max(" + stageSet.getVersion().k() + ") from " + stageSet.getTableName();
        Long version = sqlQueries.queryValue(query, Long.class);
        return version;
    }

    public Long getMaxOrder(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String query = "select coalesce (max(" + stage.getOrderPair().k() + "),0) from " + stage.getTableName() + " where " + stage.getOrderPair().k() + "=?";
        return sqlQueries.queryValue(query, Long.class, ISQLQueries.whereArgs(stageSetId));
    }

    public Stage getSubStageByName(Long parentId, String name) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getParentIdPair().k() + "=? and " + stage.getNamePair().k() + "=?";
        return sqlQueries.loadFirstRow(stage.getAllAttributes(), stage, where, ISQLQueries.whereArgs(parentId, name), Stage.class);
    }

    public ISQLResource<Stage> getObsoleteFileStagesResource(Long stageSetId) throws SqlQueriesException {
        return obsoleteStagesResource(stageSetId, false);
    }

    public ISQLResource<Stage> getObsoleteDirStagesResource(Long stageSetId) throws SqlQueriesException {
        return obsoleteStagesResource(stageSetId, true);
    }

    private ISQLResource<Stage> obsoleteStagesResource(Long stageSetId, boolean isDir) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getStageSetPair().k() + "=? and " + stage.getDeletedPair().k() + "=? and " + stage.getIsDirectoryPair().k() + "=? order by " + stage.getOrderPair().k();
        return sqlQueries.loadResource(stage.getAllAttributes(), Stage.class, where, ISQLQueries.whereArgs(stageSetId, true, isDir));
    }
}

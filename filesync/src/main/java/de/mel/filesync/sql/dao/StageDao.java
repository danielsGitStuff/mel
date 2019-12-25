package de.mel.filesync.sql.dao;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.Eva;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.UnorderedStagePair;
import de.mel.filesync.data.RootDirectory;
import de.mel.filesync.nio.FileTools;
import de.mel.filesync.sql.*;
import de.mel.sql.Dao;
import de.mel.sql.ISQLQueries;
import de.mel.sql.ISQLResource;
import de.mel.sql.SqlQueriesException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Created by xor on 11/20/16.
 */
@SuppressWarnings("Duplicates")
public class StageDao extends Dao.LockingDao {
    private final FsDao fsDao;
    private final FileSyncSettings fileSyncSettings;
    private Stage dummy = new Stage();

    public StageDao(FileSyncSettings fileSyncSettings, ISQLQueries isqlQueries, FsDao fsDao) {
        super(isqlQueries);
        this.fsDao = fsDao;
        this.fileSyncSettings = fileSyncSettings;
    }


    /**
     * finds the relating stage. starts searching from leParentDirectory and traverses Stage table till it finds it.
     * use getByPathAndName instead. This method is too expensive
     *
     * @param f
     * @return relating Stage or null if f is not staged
     */
    @Deprecated
    public Stage getStageByPath(Long stageSetId, IFile f) throws SqlQueriesException {
        RootDirectory rootDirectory = fileSyncSettings.getRootDirectory();
        String rootPath = rootDirectory.getPath();
        //todo throw Exception if f is not in rootDirectory
        if (f.getAbsolutePath().length() < rootPath.length())
            return null;
        IFile ff = AbstractFile.instance(f.getAbsolutePath());
        Stack<IFile> fileStack = FileTools.getFileStack(rootDirectory, ff);
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

//    /**
//     * finds the relating stage. starts searching from leParentDirectory and traverses Stage table till it finds it.
//     * use getByPathAndName instead. This method is too expensive
//     *
//     * @param f
//     * @return relating Stage or null if f is not staged
//     */
//    @Deprecated
//    public Stage getStageByPath(Long stageSetId, IFile f) throws SqlQueriesException {
//        RootDirectory rootDirectory = fileSyncSettings.getRootDirectory();
//        String rootPath = rootDirectory.getPath();
//        //todo throw Exception if f is not in rootDirectory
//        if (f.getAbsolutePath().length() < rootPath.length())
//            return null;
//        IFile ff = AbstractFile.instance(f.getAbsolutePath());
//        Stack<IFile> fileStack = FileTools.getFileStack(rootDirectory, ff);
//        FsEntry bottomFsEntry = fsDao.getBottomFsEntry(fileStack);
//        Stage bottomStage = this.getStageByFsId(bottomFsEntry.getId().v(), stageSetId);
//        while (!fileStack.empty()) {
//            String name = fileStack.pop().getName();
//            if (bottomStage == null)
//                continue;
//            bottomStage = this.getSubStageByNameAndParent(stageSetId, bottomStage.getId(), name);
//        }
//        return bottomStage;
//    }

    public Stage getStageByPathAndName(Long stageSetId, String path, String name) throws SqlQueriesException {
        String where = dummy.getStageSetPair().k() + "=? and " + dummy.getPathPair().k() + "=? and " + dummy.getNamePair().k() + "=?";
        return sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(stageSetId, path, name), Stage.class);
    }

    public StageSet getStageSetById(Long stageSetId) throws SqlQueriesException {
        StageSet dummy = new StageSet();
        List<StageSet> result = sqlQueries.load(dummy.getAllAttributes(), dummy, dummy.getId().k() + "=?", ISQLQueries.args(stageSetId));
        if (result.size() == 1)
            return result.get(0);
        return null;
    }

    public void deleteStageById(Long id) throws SqlQueriesException {
        Stage dummy = new Stage();
        sqlQueries.delete(dummy, dummy.getIdPair().k() + "=?", ISQLQueries.args(id));
    }

    public boolean stageSetHasContent(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String query = "select count(*) from " + dummy.getTableName() + " where " + dummy.getStageSetPair().k() + "=?";
        Integer res = sqlQueries.queryValue(query, Integer.class, ISQLQueries.args(stageSetId));
        return res > 0;
    }

    public void updateStageSet(StageSet stageSet) throws SqlQueriesException {
        sqlQueries.update(stageSet, stageSet.getId().k() + "=?", ISQLQueries.args(stageSet.getId().v()));
    }

    /**
     * gets you StageSets that have their origin in things happened on your file system.
     * this also includes previously merged StageSets
     *
     * @return
     * @throws SqlQueriesException
     */
    public List<StageSet> getStagedStageSetsFromFS() throws SqlQueriesException {
        StageSet dummy = new StageSet();
        String where = "(" + dummy.getSource().k() + "=? or " + dummy.getSource().k() + "=?) and " + dummy.getStatus().k() + "=? order by " + dummy.getCreated().k();
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(FileSyncStrings.STAGESET_SOURCE_FS, FileSyncStrings.STAGESET_SOURCE_MERGED, FileSyncStrings.STAGESET_STATUS_STAGED));
    }

    public List<StageSet> getUpdateStageSetsFromServer() throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        String where = stageSet.getSource().k() + "=? and " + stageSet.getStatus().k() + "=?";
        return sqlQueries.load(stageSet.getAllAttributes(), stageSet, where, ISQLQueries.args(FileSyncStrings.STAGESET_SOURCE_SERVER, FileSyncStrings.STAGESET_STATUS_STAGED));
    }

    /**
     * @param stageSetId
     * @return SQLResource ordered by "created" timestamp
     * @throws SqlQueriesException
     */
    public ISQLResource<Stage> getStagesResource(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getStageSetPair().k() + "=? order by " + stage.getOrderPair().k();
        return sqlQueries.loadResource(stage.getAllAttributes(), Stage.class, where, ISQLQueries.args(stageSetId));
    }

    /**
     * @param stageSetId
     * @return SQLResource ordered by "created" timestamp
     * @throws SqlQueriesException
     */
    public ISQLResource<Stage> getNotMergedStagesResource(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getStageSetPair().k() + "=? and " + stage.getMergedPair().k() + "=? order by " + stage.getOrderPair().k();
        return sqlQueries.loadResource(stage.getAllAttributes(), Stage.class, where, ISQLQueries.args(stageSetId, false));
    }


    @Deprecated
    public IFile getFileByStage(Stage stage) throws SqlQueriesException {
        RootDirectory rootDirectory = fileSyncSettings.getRootDirectory();
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
        IFile file = fsDao.getFileByFsFile(rootDirectory, bottomFsEntry);
        StringBuilder path = new StringBuilder(file.getAbsolutePath());
        while (!stageStack.empty()) {
            path.append(File.separator).append(stageStack.pop().getName());
        }
        return AbstractFile.instance(path.toString());
    }

    public void deleteServerStageSets() throws SqlQueriesException {
        StageSet stageSet = new StageSet();
        String where = stageSet.getSource().k() + "=? and " + stageSet.getStatus().k() + "=?";
        sqlQueries.delete(stageSet, where, ISQLQueries.args(FileSyncStrings.STAGESET_SOURCE_SERVER, FileSyncStrings.STAGESET_STATUS_STAGED));
    }

    public Stage getNotFlaggedStage(long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getStageSetPair().k() + "=? and " + stage.getMergedPair().k() + "=? order by " + stage.getOrderPair().k() + " limit 1";
        List<Stage> stages = sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.args(stageSetId, false));
        if (stages.size() > 0)
            return stages.get(0);
        return null;
    }

    public List<Stage> getDeletedDirectories(Long stageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getIsDirectoryPair().k() + "=? and " + stage.getStageSetPair().k() + "=? and " + stage.getDeletedPair().k() + "=?";
        return sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.args(true, stageSetId, true));
    }

    public Stage getStartStage(Long lStageSetId) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getIsDirectoryPair().k() + "=? and " + stage.getStageSetPair().k() + "=? and " + stage.getFsParentIdPair().k() + " is null limit 1";
        List<Stage> stages = sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.args(true, lStageSetId));
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
        List<Stage> stages = sqlQueries.load(stage.getAllAttributes(), stage, where, ISQLQueries.args(inode, FileSyncStrings.STAGESET_SOURCE_FS));
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
        // todo debug
        if (stage.getStageSetPair().equalsValue(3L) && stage.getNamePair().equalsValue("sub1")) {
            Lok.debug("debug");
        }
        Long id = sqlQueries.insert(stage);
        return stage.setId(id);
    }

    public ISQLResource<Stage> getStagesByStageSet(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? order by " + dummy.getOrderPair().k() + " asc";
        return sqlQueries.loadResource(dummy.getAllAttributes(), Stage.class, where, ISQLQueries.args(stageSetId));
    }

    private ISQLResource<Stage> getDeletedStagesByStageSetImpl(Long stageSetId, boolean deleted, boolean isDir) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? and "
                + dummy.getDeletedPair().k() + "=? and "
                + dummy.getIsDirectoryPair().k() + "=? order by " + dummy.getOrderPair().k() + " asc";
        return sqlQueries.loadResource(dummy.getAllAttributes(), Stage.class, where, ISQLQueries.args(stageSetId, deleted, isDir));
    }

    public ISQLResource<Stage> getDeletedDirectoryStagesByStageSet(Long stageSetId) throws SqlQueriesException {
        return getDeletedStagesByStageSetImpl(stageSetId, true, true);
    }

    public ISQLResource<Stage> getDeletedFileStagesByStageSet(Long stageSetId) throws SqlQueriesException {
        return getDeletedStagesByStageSetImpl(stageSetId, true, false);
    }

    public ISQLResource<Stage> getNotDeletedStagesByStageSet(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? and " + dummy.getDeletedPair().k() + "=? order by " + dummy.getOrderPair().k() + " asc";
        return sqlQueries.loadResource(dummy.getAllAttributes(), Stage.class, where, ISQLQueries.args(stageSetId, false));
    }

    @Deprecated
    public List<Stage> getStagesByStageSetForCommit(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? and not(" + dummy.getFsIdPair().k() + " is null and " + dummy.getDeletedPair().k() + "=?) order by " + dummy.getIdPair().k() + " asc";
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(true, stageSetId));
    }

    public ISQLResource<Stage> getStagesByStageSetForCommitResource(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? and not(" + dummy.getFsIdPair().k() + " is null and " + dummy.getDeletedPair().k() + "=?) order by " + dummy.getIdPair().k() + " asc";
        return sqlQueries.loadResource(dummy.getAllAttributes(), Stage.class, where, ISQLQueries.args(stageSetId, true));
    }

    public List<Stage> getStagesByStageSetAsList(Long stageSetId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getStageSetPair().k() + "=? order by " + dummy.getIdPair().k() + " asc";
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(stageSetId));
    }

    public Stage getStageById(Long id) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getIdPair().k() + "=?";
        List<Stage> res = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(id));
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public StageSet createStageSet(String type, Long originCertId, String originServiceUuid, Long version, long basedOnVersion) throws SqlQueriesException {
        return createStageSet(type, FileSyncStrings.STAGESET_STATUS_STAGING, originCertId, originServiceUuid, version, basedOnVersion);
    }

    public StageSet createStageSet(String type, String status, Long originCertId, String originServiceUuid, Long version, long basedOnVersion) throws SqlQueriesException {
        StageSet stageSet = new StageSet().setSource(type).setOriginCertId(originCertId)
                .setOriginServiceUuid(originServiceUuid).setStatus(status).setVersion(version)
                .setBasedOnVersion(basedOnVersion);
        Long id = sqlQueries.insert(stageSet);

        if (id == 3) {
            Lok.warn("debugl");
            Eva.eva((eva, count) -> {
                Lok.debug("ins " + count);
            });
        }
        if (id == 4) {
            Lok.warn("debug");
            Eva.eva((eva, count) -> {
                Lok.debug("ins " + count);
            });
        }
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
        String where = dummy.getStageSetPair().k() + "=? and " + dummy.getParentIdPair().k() + "=? and " + dummy.getNamePair().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(stageSetId);
        args.add(parentId);
        args.add(name);
        List<Stage> res = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public void update(Stage stage) throws SqlQueriesException {
        StageSet stageSet = this.getStageSetById(stage.getStageSet());
        String where = stage.getIdPair().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(stage.getId());
        sqlQueries.update(stage, where, args);
    }


    /**
     * "converts" a stage to an FsFile/FsDirectory. RETAINS version if available. If not
     *
     * @param stage
     * @return
     * @throws SqlQueriesException
     */
    public FsEntry stage2FsEntry(Stage stage) throws SqlQueriesException {
        FsEntry fsEntry;
        if (stage.getIsDirectory()) {
            FsDirectory fsDirectory = fsDao.getDirectoryById(stage.getFsId());
            if (fsDirectory == null) {
                fsDirectory = new FsDirectory();
            }
            fsDirectory.getContentHash().v(stage.getContentHash());
            fsEntry = fsDirectory;
        } else {
            FsFile fsFile = fsDao.getFile(stage.getFsId());
            if (fsFile == null) {
                fsFile = new FsFile();
            }
            fsFile.getContentHash().v(stage.getContentHash());
            fsFile.getSize().v(stage.getSize());
            fsFile.getSynced().v(stage.getSynced());
            fsEntry = fsFile;
        }
        fsEntry.getVersion().v(stage.getVersion());
        fsEntry.getParentId().v(stage.getFsParentId());
        fsEntry.getId().v(stage.getFsId());
        fsEntry.getName().v(stage.getName());
        //todo rer5
        fsEntry.getiNode().v(stage.getiNode());
        fsEntry.getModified().v(stage.getModified());
        fsEntry.getCreated().v(stage.getCreated());
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
        String where = dummy.getParentIdPair().k() + "=? and " + dummy.getDeletedPair().k() + "=? order by " + dummy.getOrderPair().k() + " asc";
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(stageId, false));
    }

    public ISQLResource<de.mel.filesync.sql.Stage> getFilesAsResource(Long stageSetId) throws IllegalAccessException, SqlQueriesException, InstantiationException {
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
        sqlQueries.execute(statement, ISQLQueries.args(found, stageId));
    }

    public void deleteIdenticalToFs(long stageSetId) throws SqlQueriesException {
        FsFile f = new FsFile();
        Stage s = new Stage();
        //DELETE FROM stage where  id in (select s.id from stage s left join fsentry f on s.fsid=f.id where s.stageset=244 and s.contenthash=f.contenthash and s.deleted=false)
        String statement = "delete from " + s.getTableName() + " where id in (select s." + s.getIdPair().k() + " from " + s.getTableName() + " s left join " + f.getTableName()
                + " f on f." + f.getId().k() + "=s." + s.getFsIdPair().k()
                + " where s." + s.getStageSetPair().k() + "=? and s." + s.getContentHashPair().k() + "=f." + f.getContentHash().k() + " and s." + s.getDeletedPair().k() + "=?)";
        sqlQueries.execute(statement, ISQLQueries.args(stageSetId, false));
    }

    public void updateInodeAndModifiedAndSynced(Long id, Long iNode, Long modified, Boolean synced) throws SqlQueriesException {
        Stage stage = new Stage();
        String statement = "update " + stage.getTableName() + " set " + stage.getiNodePair().k() + "=?, "
                + stage.getModifiedPair().k() + "=?, "
                + stage.getSyncedPair().k() + "=? "
                + "where " + stage.getIdPair().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.args(iNode, modified, synced, id));
    }

    public void flagMergedStageSet(Long stageSetId, boolean merged) throws SqlQueriesException {
        Stage stage = new Stage();
        String stmt = "update " + stage.getTableName() + " set " + stage.getMergedPair().k() + "=? where " + stage.getStageSetPair().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.args(merged, stageSetId));
    }

    public void flagSynced(Long id, boolean synced) throws SqlQueriesException {
        Stage stage = new Stage();
        String stmt = "update " + stage.getTableName() + " set " + stage.getSyncedPair().k() + "=? where " + stage.getIdPair().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.args(synced, id));
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
        return sqlQueries.queryValue(query, Long.class, ISQLQueries.args(stageSetId));
    }

    public Stage getSubStageByName(Long parentId, String name) throws SqlQueriesException {
        Stage stage = new Stage();
        String where = stage.getParentIdPair().k() + "=? and " + stage.getNamePair().k() + "=?";
        return sqlQueries.loadFirstRow(stage.getAllAttributes(), stage, where, ISQLQueries.args(parentId, name), Stage.class);
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
        return sqlQueries.loadResource(stage.getAllAttributes(), Stage.class, where, ISQLQueries.args(stageSetId, true, isDir));
    }

    public void devUpdateSyncedByHashSet(Long stageSetId, Set<String> availableHashes) throws SqlQueriesException {
        Stage stage = new Stage();
        String statement = "update " + stage.getTableName() + " set " + stage.getSyncedPair().k() + "=? where "
                + stage.getStageSetPair().k() + "=? and " + stage.getContentHashPair().k() + " in ";
        statement += ISQLQueries.buildPartIn(availableHashes);
        List<Object> whereArgs = ISQLQueries.args(stageSetId, true);
        whereArgs.addAll(availableHashes);
        sqlQueries.execute(statement, whereArgs);
    }

    /**
     * Find a pair of stages that are dependent on each other via fsId/fsParentId but have the wrong order.
     * If the order is not in depth first search form it has to be repaired.
     *
     * @param stageSetId the StageSet you want to repair
     * @return pair of id and order values that has to be fixed or null if there is nothing left to repair.
     * @throws SqlQueriesException
     */
    public UnorderedStagePair getUnorderedStagePair(Long stageSetId) throws SqlQueriesException {
        UnorderedStagePair u = new UnorderedStagePair();
        Stage s = new Stage();
        String statement = "select s." + s.getIdPair().k() + " as " + u.getSmallId().k()
                + ", t." + s.getIdPair().k() + " as " + u.getBigId().k()
                + ", s." + s.getOrderPair().k() + " as " + u.getSmallOrder().k()
                + ", t." + s.getOrderPair().k() + " as " + u.getBigOrder().k() + " from " + u.getTableName() + " s left join"
                + " (select * from " + u.getTableName() + " where " + s.getStageSetPair().k() + " = ?) t on s." + s.getFsParentIdPair().k() + " = t." + s.getFsIdPair().k()
                + " where s." + s.getStageSetPair().k() + " = ?  and "
                + u.getSmallOrder().k() + " < " + u.getBigOrder().k()
                + " and " + u.getBigOrder().k() + " not null order by " + u.getSmallOrder().k()
                + " limit 1;";
        List<UnorderedStagePair> list = sqlQueries.loadString(u.getAllAttributes(), u, statement, ISQLQueries.args(stageSetId, stageSetId));
        if (list.size() > 0)
            return list.get(0);
        return null;
    }

    public void updateOrder(Long stageId, Long order) throws SqlQueriesException {
        Stage s = new Stage();
        String stmt = "update " + s.getTableName() + " set " + s.getOrderPair().k() + "=? where " + s.getIdPair().k() + "=?";
        Lok.debug(stmt + " with values " + stageId + ", " + order);
        sqlQueries.execute(stmt, ISQLQueries.args(order, stageId));
    }

    public void repairParentIdsByFsParentIds(Long stageSetId) throws SqlQueriesException {
        Stage s = new Stage();
        String statement = "update " + s.getTableName() + " set " + s.getParentIdPair().k() + " = (select " + s.getIdPair().k()
                + " from " + s.getTableName() + " s where " + s.getStageSetPair().k() + " = ? and s." + s.getFsIdPair().k()
                + " = " + s.getTableName() + "." + s.getFsParentIdPair().k() + ") where " + s.getStageSetPair().k() + " = ?";
        sqlQueries.execute(statement, ISQLQueries.args(stageSetId, stageSetId));
    }


    public Stage getStageParentByFsId(StageSet stageSet, Long fsId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getFsIdPair().k() + "=? and " + dummy.getStageSetPair().k() + "=?";
        return sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(fsId, stageSet), Stage.class);
    }

    public Stage getStageParentByFsId(long stageSetId, Long fsId) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getFsIdPair().k() + "=? and " + dummy.getStageSetPair().k() + "=?";
        return sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(fsId, stageSetId), Stage.class);
    }

    public Stage getStageParentByParentFsId(long stageSetId, Long parentFsID) throws SqlQueriesException {
        Stage dummy = new Stage();
        String where = dummy.getFsParentIdPair().k() + "=? and " + dummy.getStageSetPair().k() + "=?";
        return sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(parentFsID, stageSetId), Stage.class);
    }

    public Integer getDepth(Long id) throws SqlQueriesException {
        Stage dummy = new Stage();
        String query = "select " + dummy.getDepthPair().k() + " from " + dummy.getTableName() + " where " + dummy.getIdPair().k() + "=?";
        return sqlQueries.queryValue(query, Integer.class, ISQLQueries.args(id));
    }

    public List<Integer> getMaxDepth(long id) throws SqlQueriesException {
        String query = "select distinct " + dummy.getDepthPair().k() + " from " + dummy.getTableName() + " where " + dummy.getStageSetPair().k() + "=? order by " + dummy.getDepthPair().k();
        return sqlQueries.loadColumn(dummy.getDepthPair(), Integer.class, query, ISQLQueries.args(id));
    }

    /**
     * Negates the order column of a stageset and substracts 1.
     *
     * @param stageSet
     */
    public void negateOrder(long stageSet) throws SqlQueriesException {
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getOrderPair().k() + "=-" + dummy.getOrderPair().k() + "-1 where " + dummy.getStageSetPair().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.args(stageSet));
    }

    public Stage getStageForDiving(long stageSet, int depth) throws SqlQueriesException {
        String where = dummy.getStageSetPair().k() + "=? and " + dummy.getDepthPair().k() + "=? and " + dummy.getOrderPair().k() + "<0 order by " + dummy.getIsDirectoryPair().k() + " desc";
        return sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(stageSet, depth), Stage.class);
    }

    @Nullable
    public Stage getChildForDiving(long id) throws SqlQueriesException {
        String where = dummy.getParentIdPair().k() + "=? and " + dummy.getOrderPair().k() + "<0 order by " + dummy.getIsDirectoryPair().k() + " desc";
        return sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(id), Stage.class);
    }

    public Stage getDepthAndPathAndName(long id) throws SqlQueriesException {
        String where = dummy.getIdPair().k() + "=?";
        return sqlQueries.loadFirstRow(dummy.getDepthAndPath(), dummy, where, ISQLQueries.args(id), Stage.class);
    }

    @NotNull
    public List<Stage> getNotDeletedContent(@NotNull Long directoryStageId) throws SqlQueriesException {
        String where = dummy.getParentIdPair().k() + "=? and " + dummy.getDeletedPair().k() + "=?";
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.args(directoryStageId, false));
    }

    public void updateContentHash(long id, @NotNull String hash) throws SqlQueriesException {
        String statement = "update " + dummy.getTableName() + " set " + dummy.getContentHashPair().k() + "=? where " + dummy.getIdPair().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.args(hash, id));
    }

    public boolean isInDeletionSet(Stage stage) throws SqlQueriesException {
        // select stageset,name, path||name||"%" from stage where "/b/bb/" like path||"%" and deleted=1 and stageset=2;
        String query = "select ";
        Boolean result = sqlQueries.queryValue(query, Boolean.class, ISQLQueries.args());
        return false;
    }
}

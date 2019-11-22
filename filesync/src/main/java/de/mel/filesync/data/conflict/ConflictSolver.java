package de.mel.filesync.data.conflict;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.N;
import de.mel.auth.tools.Order;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.filesync.index.InitialIndexConflictHelper;
import de.mel.filesync.service.sync.SyncStageMerger;
import de.mel.filesync.sql.*;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.sql.SqlQueriesException;

import java.io.File;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Created by xor on 5/30/17.
 */
public class ConflictSolver extends SyncStageMerger {
    private final String identifier;
    private final StageSet serverStageSet, localStageSet;
    private final RootDirectory rootDirectory;
    private final Long basedOnVersion;
    protected Map<String, Conflict> deletedParents = new HashMap<>();
    private Map<String, Conflict> conflicts = new HashMap<>();
    private Order order;
    private Map<Long, Long> oldeNewIdMap, oldeObsoleteMap;
    private StageDao stageDao;
    /**
     * obsoleteStageSet: if you decide to go for the server StageSet, some local (but not yet committed changes) must be deleted
     */
    private StageSet mergeStageSet, obsoleteStageSet;
    private final Order obsoleteOrder;


    private FsDao fsDao;
    private List<ConflictSolverListener> listeners = new ArrayList<>();
    private Semaphore listenerSemaphore = new Semaphore(1, true);
    private boolean obsolete = false;
    private boolean solving = false;
    private String conflictHelperUuid;

    public ConflictSolver(FileSyncDatabaseManager fileSyncDatabaseManager, StageSet serverStageSet, StageSet localStageSet) throws SqlQueriesException {
        super(serverStageSet.getId().v(), localStageSet.getId().v());
        this.rootDirectory = fileSyncDatabaseManager.getFileSyncSettings().getRootDirectory();
        this.serverStageSet = serverStageSet;
        this.localStageSet = localStageSet;
        stageDao = fileSyncDatabaseManager.getStageDao();
        fsDao = fileSyncDatabaseManager.getFsDao();
        identifier = createIdentifier(serverStageSet.getId().v(), localStageSet.getId().v());
        basedOnVersion = localStageSet.getBasedOnVersion().v() >= serverStageSet.getBasedOnVersion().v() ? localStageSet.getBasedOnVersion().v() : serverStageSet.getBasedOnVersion().v();
        obsoleteStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_SERVER, FileSyncStrings.STAGESET_STATUS_DELETE, serverStageSet.getOriginCertId().v(), serverStageSet.getOriginServiceUuid().v(), serverStageSet.getVersion().v(), basedOnVersion);
        obsoleteOrder = new Order();
    }

    public StageSet getServerStageSet() {
        return serverStageSet;
    }

    public StageSet getLocalStageSet() {
        return localStageSet;
    }

    public StageSet getObsoleteStageSet() {
        return obsoleteStageSet;
    }

    public static String createIdentifier(Long lStageSetId, Long rStageSetId) {
        return lStageSetId + ":" + rStageSetId;
    }

    private Map<Long, Conflict> leftIdConflictsMap = new HashMap<>();
    private Map<Long, Conflict> rightIdConflictsMap = new HashMap<>();

    /**
     * will try to merge first. if it fails the merged {@link StageSet} is removed,
     * conflicts are collected and must be resolved elsewhere (eg. ask the user for help)
     *
     * @param left
     * @param right
     * @throws SqlQueriesException
     */
    @Override
    public void stuffFound(Stage left, Stage right, IFile lFile, IFile rFile) throws SqlQueriesException {
        if (!(left == null && lFile == null || left != null && lFile != null)) {
            System.err.println("ConflictSolver.stuffFound.e1");
        }
        if (!(right == null && rFile == null || right != null && rFile != null)) {
            System.err.println("ConflictSolver.stuffFound.e2");
        }

        if (left != null && right != null && (!left.getContentHash().equals(right.getContentHash()))) {
            String key = Conflict.createKey(left, right);
            if (!conflicts.containsKey(key)) {
                //todo oof for deletion
                Conflict conflict = createConflict(left, right);
                if ((left.getDeleted() ^ right.getDeleted()) || (left.getIsDirectory() ^ right.getIsDirectory())) {
                    // there might already exist a Conflict. in this case we have more detailed information now (on stage should be null)
                    // and therefore must replace it but retain the dependencies
                    if (deletedParents.get(lFile.getParentFile().getAbsolutePath()) != null) {
                        Conflict oldeConflict = deletedParents.get(lFile.getParentFile().getAbsolutePath());
                        conflict.dependOn(oldeConflict);
                    }
                    deletedParents.put(lFile.getAbsolutePath(), conflict);
                } else if (leftIdConflictsMap.containsKey(left.getParentId())) {
                    Conflict parent = leftIdConflictsMap.get(left.getParentId());
                    conflict.dependOn(parent);
                } else if (rightIdConflictsMap.containsKey(right.getParentId())) {
                    Conflict parent = leftIdConflictsMap.get(right.getParentId());
                    conflict.dependOn(parent);
                }
            }
            return;
        }
        if (left != null && right != null && left.getContentHash().equals(right.getContentHash()) && !left.getIsDirectory() && !right.getIsDirectory()) {
            Lok.debug("ConflictSolver.stuffFound.no conflict between " + left.getId() + " and " + right.getId());
        } else {
            if (left != null && leftIdConflictsMap.containsKey(left.getParentId())) {
                //conflictSearch(left, lFile, false);
                // this was set to createConflict(left, right); at some time. mybe bug related
                Conflict conflict = createConflict(left, right);
                Conflict parent = leftIdConflictsMap.get(left.getParentId());
                conflict.dependOn(parent);
            }
            if (right != null && rightIdConflictsMap.containsKey(right.getParentId())) {
                //conflictSearch(right, rFile, true);
                Conflict conflict = createConflict(left, right);
                Conflict parent = rightIdConflictsMap.get(right.getParentId());
                conflict.dependOn(parent);
            }
        }
    }

    private void conflictSearch(Stage stage, File stageFile, final boolean stageIsFromRight) {
        File directory;
        if (stage.getIsDirectory())
            directory = stageFile;
        else
            directory = stageFile.getParentFile();

        Set<String> conflictFreePaths = new HashSet<>();
        boolean resolved = false;
        while (!resolved && !directory.getAbsolutePath().equals(rootDirectory.getPath())) {
            if (deletedParents.containsKey(directory.getAbsolutePath())) {
                Conflict conflict = deletedParents.get(directory.getAbsolutePath());
                // it is proven that this file is not in conflict
                if (conflict == null) {
                    deletedParents.put(directory.getAbsolutePath(), null);
                    for (String path : conflictFreePaths)
                        deletedParents.put(path, null);
                } else {
                    Conflict dependentConflict = (stageIsFromRight ? createConflict(null, stage) : createConflict(stage, null));
                    dependentConflict.dependOn(conflict);
                    // we want to build a hierarchy of dependent conflicts
                    if (stage.getIsDirectory()) {
                        deletedParents.put(stageFile.getAbsolutePath(), dependentConflict);
                    }
                    resolved = true;
                }
            } else {
                conflictFreePaths.add(directory.getAbsolutePath());
            }
            directory = directory.getParentFile();
        }
    }

    private Conflict createConflict(Stage left, Stage right) {
        String key = Conflict.createKey(left, right);
        Conflict conflict = new Conflict(stageDao, left, right);
        conflicts.put(key, conflict);
        if (left != null)
            leftIdConflictsMap.put(left.getId(), conflict);
        if (right != null)
            rightIdConflictsMap.put(right.getId(), conflict);
        return conflict;
    }

    @SuppressWarnings("Duplicates")
    public void beforeStart(StageSet remoteStageSet) throws SqlQueriesException {
        order = new Order();
        deletedParents = new HashMap<>();
        oldeNewIdMap = new HashMap<>();
        oldeObsoleteMap = new HashMap<>();
        this.fsDao = fsDao;
        N.r(() -> {
            mergeStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_MERGED, remoteStageSet.getOriginCertId().v(), remoteStageSet.getOriginServiceUuid().v(), remoteStageSet.getVersion().v(), basedOnVersion);
        });
        // now lets find directories that have been deleted. so we can build nice dependencies between conflicts
        List<Stage> leftDirs = stageDao.getDeletedDirectories(lStageSetId);
        List<Stage> rightDirs = stageDao.getDeletedDirectories(rStageSetId);
        for (Stage stage : leftDirs) {
            IFile f = stageDao.getFileByStage(stage);
            Stage rStage = stageDao.getStageByPath(rStageSetId, f);
            Conflict conflict = new Conflict(stageDao, stage, rStage);
            deletedParents.put(f.getAbsolutePath(), conflict);
        }
        for (Stage stage : rightDirs) {
            IFile f = stageDao.getFileByStage(stage);
            Stage lStage = stageDao.getStageByPath(lStageSetId, f);
            Conflict conflict = new Conflict(stageDao, lStage, stage);
            deletedParents.put(f.getAbsolutePath(), conflict);
        }
    }

    private void mergeFsDirectoryWithSubStages(FsDirectory fsDirToMergeInto, Stage stageToMergeWith) throws SqlQueriesException {
        List<Stage> content = stageDao.getStageContent(stageToMergeWith.getId());
        for (Stage stage : content) {
            if (stage.getIsDirectory()) {
                if (stage.getDeleted()) {
                    fsDirToMergeInto.removeSubFsDirecoryByName(stage.getName());
                } else {
                    fsDirToMergeInto.addDummySubFsDirectory(stage.getName());
                }
            } else {
                if (stage.getDeleted()) {
                    fsDirToMergeInto.removeFsFileByName(stage.getName());
                } else {
                    fsDirToMergeInto.addDummyFsFile(stage.getName());
                }
            }
            stageDao.flagMerged(stage.getId(), true);
        }
    }

    /**
     * merges already merged {@link StageSet} with itself and predecessors (Fs->Left->Right) to find parent directories
     * which were not in the right StageSet but in the left. When a directory has changed its content
     * on the left side but not/or otherwise changed on the right
     * it is missed there and has a wrong content hash.
     *
     * @throws SqlQueriesException
     */
    public void directoryStuff() throws SqlQueriesException {
        final long oldeMergedSetId = mergeStageSet.getId().v();
        Map<Long, Long> oldeIdNewIdMapForDirectories = new HashMap<>();
        order = new Order();
        StageSet targetStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_MERGED, mergeStageSet.getOriginCertId().v(), mergeStageSet.getOriginServiceUuid().v(), mergeStageSet.getVersion().v(), basedOnVersion);
        N.sqlResource(stageDao.getStagesResource(oldeMergedSetId), stageSet -> {
            Stage rightStage = stageSet.getNext();
            while (rightStage != null) {
                if (rightStage.getIsDirectory()) {
                    FsDirectory contentHashDummy = fsDao.getDirectoryById(rightStage.getFsId());
                    List<Stage> content = null;

                    if (contentHashDummy == null) {
                        // it is not in fs. just add every child from the Stage
                        contentHashDummy = new FsDirectory();
                        content = stageDao.getStageContent(rightStage.getId());
                        for (Stage stage : content) {
                            if (!stage.getDeleted()) {
                                if (stage.getIsDirectory())
                                    contentHashDummy.addDummySubFsDirectory(stage.getName());
                                else
                                    contentHashDummy.addDummyFsFile(stage.getName());
                            }
                            stageDao.flagMerged(stage.getId(), true);
                        }
                    } else {
                        // fill with info from FS
                        List<GenericFSEntry> fsContent = fsDao.getContentByFsDirectory(contentHashDummy.getId().v());
                        contentHashDummy.addContent(fsContent);
                        mergeFsDirectoryWithSubStages(contentHashDummy, rightStage);
                    }
                    // apply delta
                    contentHashDummy.calcContentHash();
                    rightStage.setContentHash(contentHashDummy.getContentHash().v());
                }
                saveRightStage(rightStage, targetStageSet.getId().v(), oldeIdNewIdMapForDirectories);
                rightStage = stageSet.getNext();
            }
        });
        stageDao.deleteStageSet(oldeMergedSetId);
        stageDao.flagMergedStageSet(targetStageSet.getId().v(), false);
        mergeStageSet = targetStageSet;
    }

    private void saveRightStage(Stage stage, Long stageSetId, Map<Long, Long> oldeIdNewIdMapForDirectories) throws SqlQueriesException {
        Long oldeId = stage.getId();
        Long parentId = stage.getParentId();
        if (parentId != null && oldeIdNewIdMapForDirectories.containsKey(parentId)) {
            stage.setParentId(oldeIdNewIdMapForDirectories.get(parentId));
        }
        stage.setId(null);
        stage.setStageSet(stageSetId);
        stage.setOrder(order.ord());
        stageDao.insert(stage);
        if (stage.getIsDirectory())
            oldeIdNewIdMapForDirectories.put(oldeId, stage.getId());
    }

    public void cleanup() throws SqlQueriesException {
        //cleanup
        stageDao.deleteStageSet(rStageSetId);
        mergeStageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(mergeStageSet);
        stageDao.deleteStageSet(obsoleteStageSet.getId().v());
        if (!stageDao.stageSetHasContent(mergeStageSet.getId().v()))
            stageDao.deleteStageSet(mergeStageSet.getId().v());
    }

    public StageSet getMergeStageSet() {
        return mergeStageSet;
    }

    /**
     * @param serverStage from server
     * @param fsStage     from fs
     * @throws SqlQueriesException
     */
    public void solve(Stage serverStage, Stage fsStage) throws SqlQueriesException {
        Stage solvedStage = null;
        final String key = Conflict.createKey(serverStage, fsStage);
        final Conflict conflict = conflicts.remove(key);
        final Long oldeRightId = fsStage == null ? null : fsStage.getId();
        final Long oldeLeftId = serverStage == null ? null : serverStage.getId();

        // if a conflict is present, apply its solution. if not, apply the left or right.
        // they must have the same hashes.
        if (conflict != null) {
            if (conflict.isLeft()) {
                solvedStage = serverStage;
                // entry exists locally, delete
                if (fsStage != null && !fsStage.getDeleted() && solvedStage == null) {
                    if (fsStage.getParentIdPair().notNull()) {
                        fsStage.setParentId(oldeObsoleteMap.get(fsStage.getParentId()));
                    }
                    // connect to the bottom fs id available in the server stageset
                    if (fsStage.getFsIdPair().isNull() && fsStage.getFsParentIdPair().isNull()) {
                        // look for oldeId first
                        Stage parent = conflict.getDependsOn().getLeft();
                        if (parent != null)
                            fsStage.setFsParentId(parent.getFsId());
                    }
                    fsStage.setDeleted(true)
                            .setId(null)
                            .setStageSet(obsoleteStageSet.getId().v())
                            .setOrder(obsoleteOrder.ord());
                    stageDao.insert(fsStage);
                    oldeObsoleteMap.put(oldeRightId, fsStage.getId());
                }
            } else {
                solvedStage = fsStage;
                // left always comes with fs ids. copy if available
                if (serverStage != null && solvedStage != null) {
                    solvedStage.setFsId(serverStage.getFsId());
                    solvedStage.setFsParentId(serverStage.getFsParentId());
                }
                // if it does not exist locally and we rejected the remote file: copy left to delete it.
                if (serverStage != null && !serverStage.getDeleted() && solvedStage == null) {
                    if (serverStage.getParentIdPair().notNull()) {
                        serverStage.setParentId(oldeNewIdMap.get(serverStage.getParentId()));
                    }
                    serverStage.setStageSet(mergeStageSet.getId().v())
                            .setId(null)
                            .setOrder(order.ord())
                            .setDeleted(true);
                    stageDao.insert(serverStage);
                }
            }
        } else if (serverStage != null) {
            solvedStage = serverStage;
        } else if (fsStage != null) {
            solvedStage = fsStage;
        } else {
            System.err.println("ConflictSolver.solve:ERRRR");
        }
        if (solvedStage != null) {
            // adapt parent stages
            // assuming that Stage.id and Stage.parentId have not been changed yet

            if (solvedStage.getParentId() != null) {
                if (oldeNewIdMap.containsKey(solvedStage.getParentId())) {
                    solvedStage.setParentId(oldeNewIdMap.get(solvedStage.getParentId()));
                } else {
                    solvedStage.setParentId(null);
                }
            }

            solvedStage.setStageSet(mergeStageSet.getId().v());
            solvedStage.setOrder(order.ord());
            solvedStage.setId(null);
            stageDao.insert(solvedStage);
            // add these things. subsequent Conflicts might reference them.
            if (oldeLeftId != null)
                oldeNewIdMap.put(oldeLeftId, solvedStage.getId());
            if (oldeRightId != null)
                oldeNewIdMap.put(oldeRightId, solvedStage.getId());
        }
    }

    private void addOldeId(Stage stage, Long newId) {

    }


    public String getIdentifier() {
        return identifier;
    }


    /**
     * check if everything is solved and sane (eg. do not modify a file when the parent folder is deleted)
     *
     * @return
     */
    public boolean isSolved() {
        Set<Conflict> isOk = new HashSet<>();
        try {
            for (Conflict conflict : conflicts.values()) {
                recurseUp(isOk, conflict);
            }
        } catch (ConflictException.UnsolvedConflictsException | ConflictException.ContradictingConflictsException e) {
            return false;
        } catch (ConflictException e) {
            System.err.println(getClass().getSimpleName() + ".isSolved().Error: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void recurseUp(Set<Conflict> isOk, Conflict conflict) throws ConflictException {
        if (!conflict.hasDecision())
            throw new ConflictException.UnsolvedConflictsException();
        if (isOk.contains(conflict))
            return;
        final boolean deleted = conflict.getChoice().getDeleted();
        Stack<Conflict> stack = new Stack<>();
        stack.push(conflict);
        Conflict parent = conflict.getDependsOn();
        while (parent != null) {
            if (isOk.contains(parent))
                break;
            if (!parent.hasDecision())
                throw new ConflictException.UnsolvedConflictsException();
            if (!deleted && parent.getChoice().getDeleted())
                throw new ConflictException.ContradictingConflictsException();
            stack.push(parent);
            parent = parent.getDependsOn();
        }
        isOk.addAll(stack);
    }

    @Override
    public String toString() {
        return lStageSetId + " vs " + rStageSetId + " -> " + (mergeStageSet == null ? "null" : mergeStageSet.getId().v().toString());
    }

    public boolean hasConflicts() {
        return conflicts.size() > 0;
    }

    public Collection<Conflict> getConflicts() {
        return conflicts.values();
    }

    public void addListener(ConflictSolverListener conflictSolverListener) {
        N.r(() -> {
            listenerSemaphore.acquire();
            this.listeners.add(conflictSolverListener);
            if (obsolete)
                tellObsolete();
            listenerSemaphore.release();
        });

    }


    private void tellObsolete() {
        for (ConflictSolverListener listener : listeners)
            listener.onConflictObsolete();
    }

    /**
     * tells the {@link ConflictSolverListener}s they are obsolete if one of the merged {@link StageSet}s was part of this instance.
     *
     * @param mergedLeftStageSetId
     * @param mergedRightStageSetId
     */
    public void checkObsolete(Long mergedLeftStageSetId, Long mergedRightStageSetId) {
        if (this.lStageSetId.equals(mergedLeftStageSetId) || this.lStageSetId.equals(mergedRightStageSetId)
                || this.rStageSetId.equals(mergedLeftStageSetId) || this.rStageSetId.equals(mergedRightStageSetId)) {
            N.r(() -> {
                listenerSemaphore.acquire();
                this.obsolete = true;
                tellObsolete();
                listenerSemaphore.release();
            });
        }
    }

    public boolean isSolving() {
        return solving;
    }

    public void setSolving(boolean solving) {
        this.solving = solving;
    }

    public void setConflictHelperUuid(String conflictHelperUuid) {
        this.conflictHelperUuid = conflictHelperUuid;
    }

    public String getConflictHelperUuid() {
        return conflictHelperUuid;
    }

    public void probablyFinished() {
        if (conflictHelperUuid != null && isSolved())
            InitialIndexConflictHelper.finished(conflictHelperUuid);
    }

    public interface ConflictSolverListener {
        /**
         * called when the {@link ConflictSolver}s {@link StageSet}s were merged.
         */
        void onConflictObsolete();
    }
}

package de.mein.drive.data.conflict;

import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.service.sync.SyncStageMerger;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Created by xor on 5/30/17.
 */
public class ConflictSolver extends SyncStageMerger {
    private final String identifier;
    private final StageSet lStageSet, rStageSet;
    private final RootDirectory rootDirectory;
    protected Map<String, Conflict> deletedParents = new HashMap<>();
    private Map<String, Conflict> conflicts = new HashMap<>();
    private Order order;
    private Map<Long, Long> oldeNewIdMap;
    private StageDao stageDao;
    private StageSet mergeStageSet;
    private FsDao fsDao;
    private List<ConflictSolverListener> listeners = new ArrayList<>();
    private Semaphore listenerSemaphore = new Semaphore(1, true);
    private boolean obsolete = false;

    public ConflictSolver(DriveDatabaseManager driveDatabaseManager, StageSet lStageSet, StageSet rStageSet) {
        super(lStageSet.getId().v(), rStageSet.getId().v());
        this.rootDirectory = driveDatabaseManager.getDriveSettings().getRootDirectory();
        this.lStageSet = lStageSet;
        this.rStageSet = rStageSet;
        stageDao = driveDatabaseManager.getStageDao();
        fsDao = driveDatabaseManager.getFsDao();
        identifier = createIdentifier(lStageSet.getId().v(), rStageSet.getId().v());
    }

    public static String createIdentifier(Long lStageSetId, Long rStageSetId) {
        return lStageSetId + ":" + rStageSetId;
    }

    /**
     * will try to merge first. if it fails the merged {@link StageSet} is removed,
     * conflicts are collected and must be resolved elsewhere (eg. ask the user for help)
     *
     * @param left
     * @param right
     * @throws SqlQueriesException
     */
    @Override
    public void stuffFound(Stage left, Stage right, File lFile, File rFile) throws SqlQueriesException {
        if (!(left == null && lFile == null || left != null && lFile != null)) {
            System.err.println("ConflictSolver.stuffFound.e1");
        }
        if (!(right == null && rFile == null || right != null && rFile != null)) {
            System.err.println("ConflictSolver.stuffFound.e2");
        }

        if (left != null && right != null) {
            String key = Conflict.createKey(left, right);
            if (!conflicts.containsKey(key)) {
                //todo oof for deletion
                Conflict conflict = createConflict(left, right);
                if ((left.getIsDirectory() && left.getDeleted()) || (right.getIsDirectory() && right.getDeleted())) {
                    // there might already exist a Conflict. in this case we have more detailed information now (on stage should be null)
                    // and therefor must replace ist but retain the dependencies
                    if (deletedParents.get(lFile.getAbsolutePath()) != null) {
                        Conflict oldeConflict = deletedParents.get(lFile.getAbsolutePath());
                        conflict.dependOn(oldeConflict.getDependsOn());
                    }
                    deletedParents.put(lFile.getAbsolutePath(), conflict);
                }
            }
            return;
        }
        if (left != null) {
            conflictSearch(left, lFile, false);
        }
        if (right != null) {
            conflictSearch(right, rFile, true);
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
        return conflict;
    }

    public void beforeStart(StageSet remoteStageSet) throws SqlQueriesException {
        order = new Order();
        deletedParents = new HashMap<>();
        oldeNewIdMap = new HashMap<>();
        this.fsDao = fsDao;
        N.r(() -> {
            mergeStageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_FS, remoteStageSet.getOriginCertId().v(), remoteStageSet.getOriginServiceUuid().v());
        });
        // now lets find directories that have been deleted. so we can build nice dependencies between conflicts
        List<Stage> leftDirs = stageDao.getDeletedDirectories(lStageSetId);
        List<Stage> rightDirs = stageDao.getDeletedDirectories(rStageSetId);
        for (Stage stage : leftDirs) {
            Conflict conflict = new Conflict(stageDao, stage, null);
            File f = stageDao.getFileByStage(stage);
            deletedParents.put(f.getAbsolutePath(), conflict);
        }
        for (Stage stage : rightDirs) {
            Conflict conflict = new Conflict(stageDao, null, stage);
            File f = stageDao.getFileByStage(stage);
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
        StageSet targetStageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_FS, mergeStageSet.getOriginCertId().v(), mergeStageSet.getOriginServiceUuid().v());
        ISQLResource<Stage> stageSet = stageDao.getStagesResource(oldeMergedSetId);
        Stage rightStage = stageSet.getNext();
        while (rightStage != null) {
            if (rightStage.getIsDirectory()) {
                FsDirectory contentHashDummy = fsDao.getDirectoryById(rightStage.getFsId());
                if (contentHashDummy == null) {
                    // it is not in fs. just add every child from the Stage
                    contentHashDummy = new FsDirectory();
                    List<Stage> content = stageDao.getStageContent(rightStage.getId());
                    for (Stage stage : content)
                        contentHashDummy.addDummyFsFile(stage.getName());
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
        stageDao.deleteStageSet(oldeMergedSetId);
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
        mergeStageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(mergeStageSet);
        if (!stageDao.stageSetHasContent(mergeStageSet.getId().v()))
            stageDao.deleteStageSet(mergeStageSet.getId().v());
    }

    public StageSet getMergeStageSet() {
        return mergeStageSet;
    }

    public void solve(Stage left, Stage right) throws SqlQueriesException {
        Stage solvedStage = null;
        final String key = Conflict.createKey(left, right);
        if (conflicts.containsKey(key)) {
            Conflict conflict = conflicts.remove(key);
            if (conflict.isRight() && conflict.hasRight()) {
                solvedStage = right;
                if (left != null) {
                    solvedStage.setFsParentId(left.getFsParentId());
                    solvedStage.setFsId(left.getFsId());
                    solvedStage.setVersion(left.getVersion());
                    Long parentId = left.getParentId();
                    if (parentId != null) {
                        if (oldeNewIdMap.containsKey(parentId)) {
                            solvedStage.setParentId(oldeNewIdMap.get(parentId));
                        }
                    }
                }
            } else if (conflict.isLeft() && conflict.hasLeft()) {
                solvedStage = left;
                // left is server side, so it comes with the appropriate FsIds
            } else
                System.err.println(getClass().getSimpleName() + ".solve()... strange things happened");
        } else if (left != null) {
            solvedStage = left;
        } else if (right != null) {
            solvedStage = right;

        }
        if (solvedStage != null) {
            solvedStage.setOrder(order.ord());
            solvedStage.setStageSet(mergeStageSet.getId().v());

            File solvedFile = stageDao.getFileByStage(solvedStage);
            File solvedParent = solvedFile.getParentFile();

            if (deletedParents.containsKey(solvedParent.getAbsolutePath()) || deletedParents.containsKey(solvedFile.getAbsolutePath())) {
                solvedStage.setFsId(null);
                if (deletedParents.containsKey(solvedParent.getAbsolutePath())) {
                    solvedStage.setFsParentId(null);
                }
                if (!solvedStage.getIsDirectory())
                    solvedStage.setSynced(false);
            }
            // adjust ids
            Long oldeId = solvedStage.getId();
            solvedStage.setMerged(false);
            solvedStage.setId(null);
            if (oldeNewIdMap.containsKey(solvedStage.getParentId())) {
                solvedStage.setParentId(oldeNewIdMap.get(solvedStage.getParentId()));
            }
            try {
                stageDao.insert(solvedStage);
                oldeNewIdMap.put(oldeId, solvedStage.getId());
                // map new id
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
            if (solvedStage == right) {

            } else {

            }

        }

        if (solvedStage != null && solvedStage == right) {

        } else if (solvedStage != null) {
            solvedStage.setId(null);
        }
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

    public interface ConflictSolverListener {
        /**
         * called when the {@link ConflictSolver}s {@link StageSet}s were merged.
         */
        void onConflictObsolete();
    }
}

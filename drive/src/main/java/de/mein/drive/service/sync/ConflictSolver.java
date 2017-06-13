package de.mein.drive.service.sync;

import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 5/30/17.
 */
public class ConflictSolver extends SyncStageMerger {
    private final String identifier;
    private final StageSet lStageSet, rStageSet;
    private Map<String, Conflict> conflicts = new HashMap<>();
    private Order order;
    private Map<Long, Long> oldeNewIdMap;
    private StageDao stageDao;
    private StageSet mergeStageSet;
    private FsDao fsDao;


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
        assert (left == null && lFile == null || left != null && lFile != null);
        assert (right == null && rFile == null || right != null && rFile != null);

        if (left != null && right != null) {
            String key = Conflict.createKey(left, right);
            if (!conflicts.containsKey(key)) {
                //todo oof for deletion
                createConflict(left, right);
            }
            return;
        }
        if (left != null) {
            for (final String path : deletedParentRight.keySet()) {
                if (path.startsWith(lFile.getAbsolutePath())) {
                    Conflict conflictRight = deletedParentRight.get(path);
                    Conflict conflict = createConflict(left, conflictRight.getRight());
                    conflict.setDependsOn(conflictRight);
                    conflictRight.getDependents().add(conflict);
                    break;
                }
            }
            return;
        }
        if (right != null) {
            for (final String path : deletedParentLeft.keySet()) {
                if (path.startsWith(lFile.getAbsolutePath())) {
                    Conflict conflictLeft = deletedParentLeft.get(path);
                    Conflict conflict = createConflict(conflictLeft.getLeft(), right);
                    conflict.setDependsOn(conflictLeft);
                    conflictLeft.getDependents().add(conflict);
                    break;
                }
            }
            return;
        }

    }

    private Conflict createConflict(Stage left, Stage right) {
        String key = Conflict.createKey(left, right);
        Conflict conflict = new Conflict(stageDao, left, right);
        conflicts.put(key, conflict);
        return conflict;
    }

    public void beforeStart(FsDao fsDao, StageDao stageDao, StageSet remoteStageSet) {
        order = new Order();
        oldeNewIdMap = new HashMap<>();
        this.stageDao = stageDao;
        this.fsDao = fsDao;
        N.r(() -> {
            mergeStageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_FS, remoteStageSet.getOriginCertId().v(), remoteStageSet.getOriginServiceUuid().v());
        });
    }

    private void mergeFsDirectoryWithSubStages(FsDirectory fsDirToMergeInto, Stage stageToMergeWith) throws SqlQueriesException {
        List<Stage> content = stageDao.getStageContent(stageToMergeWith.getId(), stageToMergeWith.getStageSet());
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
        order = new Order();
        StageSet targetStageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_FS, mergeStageSet.getOriginCertId().v(), mergeStageSet.getOriginServiceUuid().v());
        ISQLResource<Stage> stageSet = stageDao.getStagesResource(oldeMergedSetId);
        Stage rightStage = stageSet.getNext();
        while (rightStage != null) {
            rightStage.setId(null);
            File parentFile = stageDao.getFileByStage(rightStage).getParentFile();
            // first, lets see if we have already created a parent!
            Stage alreadyStagedParent = stageDao.getStageByPath(targetStageSet.getId().v(), parentFile);
            Stage targetParentStage = new Stage()
                    .setName(parentFile.getName())
                    .setStageSet(targetStageSet.getId().v())
                    .setIsDirectory(true);
            if (alreadyStagedParent == null) {
                FsDirectory parentFsDirectory = fsDao.getFsDirectoryByPath(parentFile);
                Stage leftParentStage = stageDao.getStageByPath(lStageSet.getId().v(), parentFile);
                FsDirectory targetParentFsDirectory = new FsDirectory().setName(parentFile.getName());
                // check if right parent already exists. else...
                Stage mergedParentStage = stageDao.getStageByPath(oldeMergedSetId, parentFile);
                // add fs content
                if (parentFsDirectory != null) {
                    List<GenericFSEntry> content = fsDao.getContentByFsDirectory(parentFsDirectory.getId().v());
                    targetParentFsDirectory.addContent(content);
                    targetParentStage.setFsId(parentFsDirectory.getId().v());
                    targetParentStage.setFsParentId(parentFsDirectory.getParentId().v());
                }
                // merge with left content
                if (leftParentStage != null) {
                    mergeFsDirectoryWithSubStages(targetParentFsDirectory, leftParentStage);
                    targetParentStage.setFsId(leftParentStage.getFsId());
                    targetParentStage.setFsParentId(leftParentStage.getFsParentId());
                }
                // merge with right content
                if (mergedParentStage != null) {
                    mergeFsDirectoryWithSubStages(targetParentFsDirectory, mergedParentStage);
                    targetParentStage.setFsId(mergedParentStage.getFsId());
                    targetParentStage.setFsParentId(mergedParentStage.getFsParentId());
                }
                targetParentFsDirectory.calcContentHash();
                // stage if content hash has changed
                if (!targetParentFsDirectory.getContentHash().v().equals(leftParentStage.getContentHash())) {
                    targetParentStage.setOrder(order.ord());
                    stageDao.insert(targetParentStage);
                    rightStage.setParentId(targetParentStage.getId());
                }
            } else {
                // we already staged it!
                rightStage.setParentId(alreadyStagedParent.getId());
            }
            // copy the stage
            rightStage.setStageSet(targetStageSet.getId().v());
            rightStage.setOrder(order.ord());
            stageDao.insert(rightStage);
            rightStage = stageSet.getNext();
        }
        stageDao.deleteStageSet(oldeMergedSetId);
        mergeStageSet = targetStageSet;
    }

    public void cleanup() throws SqlQueriesException {
        //cleanup
        stageDao.deleteStageSet(rStageSetId);
        if (!stageDao.stageSetHasContent(mergeStageSet.getId().v()))
            stageDao.deleteStageSet(mergeStageSet.getId().v());
        mergeStageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(mergeStageSet);
    }

    public StageSet getMergeStageSet() {
        return mergeStageSet;
    }

    public void solve(Stage left, Stage right) {
        Stage solvedStage = null;
        if (left != null && right != null) {
            String key = Conflict.createKey(left, right);
            if (conflicts.containsKey(key)) {
                Conflict conflict = conflicts.remove(key);
                if (conflict.isRight()) {
                    try {
                        solvedStage = right;
                        solvedStage.setFsParentId(left.getFsParentId());
                        solvedStage.setFsId(left.getFsId());
                        solvedStage.setVersion(left.getVersion());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if (left != null) {
            //solvedStage = left;
        } else {
            solvedStage = right;
        }
        if (solvedStage != null) {
            try {
                solvedStage.setOrder(order.ord());
                solvedStage.setStageSet(mergeStageSet.getId().v());
                // adjust ids
                Long oldeId = solvedStage.getId();
                solvedStage.setMerged(false);
                solvedStage.setId(null);
                if (oldeNewIdMap.containsKey(solvedStage.getParentId())) {
                    solvedStage.setParentId(oldeNewIdMap.get(solvedStage.getParentId()));
                }
                stageDao.insert(solvedStage);
                oldeNewIdMap.put(solvedStage.getId(), oldeId);
                // map new id
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        }
    }


    public ConflictSolver(StageSet lStageSet, StageSet rStageSet) {
        super(lStageSet.getId().v(), rStageSet.getId().v());
        this.lStageSet = lStageSet;
        this.rStageSet = rStageSet;
        identifier = createIdentifier(lStageSet.getId().v(), rStageSet.getId().v());
    }

    public static String createIdentifier(Long lStageSetId, Long rStageSetId) {
        return lStageSetId + ":" + rStageSetId;
    }

    public String getIdentifier() {
        return identifier;
    }


    public boolean isSolved() {
        return conflicts.values().stream().allMatch(Conflict::hasDecision);
    }

    public boolean hasConflicts() {
        return conflicts.size() > 0;
    }

    public Collection<Conflict> getConflicts() {
        return conflicts.values();
    }


}

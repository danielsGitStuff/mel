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
import java.util.stream.Collectors;

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
    public void stuffFound(Stage left, Stage right) throws SqlQueriesException {
        if (left != null && right != null) {
            String key = Conflict.createKey(left, right);
            if (conflicts.containsKey(key)) {

            } else {
                addConflict(new Conflict(left, right));
            }
        }
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
     * @throws SqlQueriesException
     */
    public void directoryStuff() throws SqlQueriesException {
        final long oldeMergedSetId = mergeStageSet.getId().v();
        order = new Order();
        mergeStageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_FS, mergeStageSet.getOriginCertId().v(), mergeStageSet.getOriginServiceUuid().v());
        ISQLResource<Stage> stageSet = stageDao.getStagesResource(oldeMergedSetId);
        Stage rightStage = stageSet.getNext();
        while (rightStage != null) {
            File parentFile = stageDao.getFileByStage(rightStage).getParentFile();
            Stage leftParentStage = stageDao.getStageByPath(lStageSet.getId().v(), parentFile);
            FsDirectory leftFsDirectory = fsDao.getFsDirectoryByPath(parentFile);
            FsDirectory rightFsDirectory = new FsDirectory().setName(parentFile.getName());
            // check if right parent already exists. else...
            Stage rightParentStage = stageDao.getStageByPath(oldeMergedSetId,  parentFile);
            if (rightParentStage == null) {
                rightParentStage = new Stage().setName(parentFile.getName());
            } else {
                rightParentStage.setStageSet(mergeStageSet.getId().v());
            }
            // add fs content
            if (leftParentStage != null) {
                List<GenericFSEntry> content = fsDao.getContentByFsDirectory(leftFsDirectory.getId().v());
                rightFsDirectory.addContent(content);
            }
            // merge with left content
            if (leftParentStage != null) {
                mergeFsDirectoryWithSubStages(rightFsDirectory, leftParentStage);
            }
            // merge with right content
            if (rightParentStage != null) {
                mergeFsDirectoryWithSubStages(rightFsDirectory, rightParentStage);
            }
            rightFsDirectory.calcContentHash();
            // stage if content hash has changed
            if (!rightFsDirectory.getContentHash().v().equals(leftParentStage.getContentHash())) {

            }
            // flag the right stage as merged
            stageDao.flagMerged(rightStage.getId(), true);

            // copy the stage
            rightStage.setStageSet(mergeStageSet.getId().v());
            rightStage.setOrder(order.ord());
            stageDao.insert(rightStage);

            if (rightParentStage == null) {
                if (leftParentStage != null) {
                    // calc new ContentHash of parent

                    // add everything from FS
                    FsDirectory fsParent = fsDao.getFsDirectoryByPath(parentFile);
                    if (fsParent != null) {

                    }
                    rightFsDirectory.calcContentHash();
                    rightParentStage = new Stage().mergeValuesFrom(leftParentStage);

                    rightParentStage.setMerged(false)
                            .setContentHash(rightFsDirectory.getContentHash().v());
                    if (true) { // if delta
                        rightParentStage.setOrder(order.ord());
                        stageDao.insert(rightParentStage);
                        rightStage.setParentId(rightParentStage.getId())
                                .setFsParentId(rightParentStage.getFsParentId())
                                .setFsId(rightParentStage.getFsId());
                    }
                }
            }

            rightStage = stageSet.getNext();
        }
        stageDao.deleteStageSet(oldeMergedSetId);
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

    public void addConflict(Conflict conflict) {
        conflicts.put(conflict.getKey(), conflict);
    }

    public boolean isSolved() {
        return conflicts.values().stream().allMatch(Conflict::hasDecision);
    }

    public boolean hasConflicts() {
        return conflicts.size() > 0;
    }

    public List<Stage> getAllLeft() {
        return conflicts.values().stream().map(Conflict::getLeft).collect(Collectors.toList());
    }

    public List<Stage> getAllRight() {
        return conflicts.values().stream().map(Conflict::getRight).collect(Collectors.toList());
    }

    public Collection<Conflict> getConflicts() {
        return conflicts.values();
    }


}

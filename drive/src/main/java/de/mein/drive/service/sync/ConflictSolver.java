package de.mein.drive.service.sync;

import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.SqlQueriesException;

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

    public void beforeStart(StageDao stageDao, StageSet remoteStageSet) {
        order = new Order();
        oldeNewIdMap = new HashMap<>();
        this.stageDao = stageDao;
        N.r(() -> {
            mergeStageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_FS
                    , remoteStageSet.getOriginCertId().v(), remoteStageSet.getOriginServiceUuid().v());
        });
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

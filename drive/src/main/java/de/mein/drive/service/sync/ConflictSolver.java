package de.mein.drive.service.sync;

import de.mein.auth.tools.Order;
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

    public void beforeStart() {
        order = new Order();
    }

    public void solve(StageDao stageDao, Stage parent, Stage left, Stage right) {
        Stage solvedStage = null;
        if (left != null && right != null) {
            String key = Conflict.createKey(left, right);
            if (conflicts.containsKey(key)) {
                Conflict conflict = conflicts.get(key);
                solvedStage = conflict.getChoice();
                if (conflict.isRight()) {
                    solvedStage.
                }
            }
        } else if (left != null) {

        } else {

        }
        if (solvedStage != null) {
            solvedStage.setOrder(order.ord());
        } else {
            System.err.println(getClass().getSimpleName() + ".j9f4n30043t30");
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

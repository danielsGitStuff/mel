package de.mein.drive.service.sync;

import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.sql.SqlQueriesException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 5/30/17.
 */
public class ConflictCollection extends SyncStagesComparator {
    private final String identifier;
    private final StageSet lStageSet, rStageSet;
    private Map<String, Conflict> conflicts = new HashMap<>();

    @Override
    public void stuffFound(Stage left, Stage right) throws SqlQueriesException {
        if (left != null && right != null) {
            String key = Conflict.createKey(left,right);
            if (!conflicts.containsKey(key))
                addConflict(new Conflict(left, right));
        }
    }

    public ConflictCollection(StageSet lStageSet, StageSet rStageSet) {
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
        conflicts.put(conflict.getKey(),conflict);
    }

    public boolean isSolved() {
       return conflicts.values().stream().allMatch(Conflict::hasDecision);
    }

    public boolean hasConflicts() {
        return conflicts.size() > 0;
    }
}

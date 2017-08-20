package de.mein.drive.data.conflict;

import de.mein.drive.sql.Stage;
import de.mein.drive.sql.dao.StageDao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by xor on 5/30/17.
 */
public class Conflict {

    private Stage lStage;
    private Stage rStage;
    private Long lStageId, rStageId;
    private StageDao stageDao;
    private Boolean isRight;
    private String key;
    private Set<Conflict> dependents = new HashSet<>();
    private Conflict dependsOn;

    public Conflict(StageDao stageDao, Stage lStage, Stage rStage) {
        this.lStageId = lStage != null ? lStage.getId() : null;
        this.rStageId = rStage != null ? rStage.getId() : null;
        this.lStage = lStage;
        this.rStage = rStage;
        this.stageDao = stageDao;
        key = createKey(lStage, rStage);
    }


    public Conflict() {

    }

    public static String createKey(Stage lStage, Stage rStage) {
        return (lStage != null ? lStage.getId() : "n") + "/" + (rStage != null ? rStage.getId() : "n");
    }

    public Conflict chooseRight() {
        isRight = true;
        for (Conflict sub : dependents) {
            sub.chooseRight();
        }
        return this;
    }

    public Conflict chooseLeft() {
        isRight = false;
        for (Conflict sub : dependents) {
            sub.chooseLeft();
        }
        return this;
    }

    public boolean isRight() {
        return isRight != null && isRight;
    }

    public Stage getChoice() {
        if (isRight) {
            if (rStage != null)
                return rStage;
            else
                return dependsOn.getChoice();
        } else {
            if (lStage != null)
                return lStage;
            else
                return dependsOn.getChoice();
        }
    }

    public boolean hasDecision() {
        return isRight != null;
    }

    public String getKey() {
        return key;
    }

    public Stage getLeft() {
        if (lStageId == null)
            return null;
        return lStage;
    }

    public Stage getRight() {
        if (rStageId == null)
            return null;
        return rStage;
    }

    @Override
    public String toString() {
        return "{class:\"" + getClass().getSimpleName() + "\",key:\"" + key + "\",isRight:\"" + isRight + "\"}";
    }


    public void chooseNothing() {
        isRight = null;
    }

    public boolean isLeft() {
        return isRight != null && !isRight;
    }

    public Conflict dependOn(Conflict dependsOn) {
        this.dependsOn = dependsOn;
        if (dependsOn != null)
            dependsOn.dependents.add(this);
        return this;
    }

    public Conflict getDependsOn() {
        return dependsOn;
    }

    public Set<Conflict> getDependents() {
        return dependents;
    }

    public boolean hasLeft() {
        return lStageId != null;
    }

    public boolean hasRight() {
        return rStageId != null;
    }

    /**
     * sorts, indents and adds empty rows
     *
     * @param conflicts
     * @return
     */
    public static List<Conflict> prepareConflicts(Collection<Conflict> conflicts) {
        List<Conflict> result = new ArrayList<>();
        List<Conflict> rootConflicts = getRootConflicts(conflicts);
        for (Conflict root : rootConflicts) {
            result.add(root);
            traversalAdding(result, root.getDependents());
            if (root.getDependents().size() > 0)
                result.add(new EmptyRowConflict());
        }
        return result;
    }

    private static void traversalAdding(List<Conflict> result, Set<Conflict> stuffToTraverse) {
        for (Conflict conflict : stuffToTraverse) {
            result.add(conflict);
            if (conflict.getDependents().size() > 0) {
                traversalAdding(result, conflict.getDependents());
            }
        }
    }

    public Conflict getDependentByName(String name) {
        for (Conflict dep : dependents) {
            if (dep.getLeft() != null && dep.getLeft().getName().equals(name))
                return dep;
            if (dep.getRight() != null && dep.getRight().getName().equals(name))
                return dep;
        }
        return null;
    }

    public static List<Conflict> getRootConflicts(Collection<Conflict> conflicts) {
        List<Conflict> rootConflicts = new ArrayList<>();
        for (Conflict conflict : conflicts) {
            if (conflict.getDependsOn() == null)
                rootConflicts.add(conflict);
        }
        return rootConflicts;
    }
}

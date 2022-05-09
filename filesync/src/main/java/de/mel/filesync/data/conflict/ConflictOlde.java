//package de.mel.filesync.data.conflict;
//
//import de.mel.Lok;
//import de.mel.auth.tools.Eva;
//import de.mel.filesync.sql.Stage;
//import de.mel.filesync.sql.dao.StageDao;
//
//import java.util.*;
//
///**
// * Created by xor on 5/30/17.
// */
//public class ConflictOlde {
//
//    private Stage lStage;
//    private Stage rStage;
//    private Long lStageId, rStageId;
//    private StageDao stageDao;
//    private Boolean isRight;
//    private String key;
//    private Set<ConflictOlde> dependents = new HashSet<>();
//    private ConflictOlde dependsOn;
//
//    public ConflictOlde(StageDao stageDao, Stage lStage, Stage rStage) {
//        this.lStageId = lStage != null ? lStage.getId() : null;
//        this.rStageId = rStage != null ? rStage.getId() : null;
//        this.lStage = lStage;
//        this.rStage = rStage;
//        this.stageDao = stageDao;
//        key = createKey(lStage, rStage);
//        // todo debug
//        Eva.condition(key.equals("17/6"),2,() -> {
//            Lok.debug();
//        });
//        if (lStageId != null && rStageId != null && lStageId == 18 && rStageId == 40)
//            Lok.debug("ConflictOlde.ConflictOlde");
//        Eva.eva((eva, count) -> {
//            if (lStageId != null && rStageId != null && lStageId == 18 && rStageId == 40)
//                eva.error();
//        });
//    }
//
//
//    public ConflictOlde() {
//
//    }
//
//
//    public static String createKey(Stage lStage, Stage rStage) {
//        return (lStage != null ? lStage.getId() : "n") + "/" + (rStage != null ? rStage.getId() : "n");
//    }
//
//    public ConflictOlde chooseRight() {
//        isRight = true;
//        for (ConflictOlde sub : dependents) {
//            sub.chooseRight();
//        }
//        return this;
//    }
//
//    public ConflictOlde chooseLeft() {
//        isRight = false;
//        for (ConflictOlde sub : dependents) {
//            sub.chooseLeft();
//        }
//        return this;
//    }
//
//    public boolean isRight() {
//        return isRight != null && isRight;
//    }
//
//    /**
//     * get you the stage that was chosen. that might be a directory higher in the hierarchy.
//     * it isn't necessarily the parent stage.
//     *
//     * @return
//     */
//    public Stage getChoice() {
//        if (isRight == null)
//            return null;
//        if (isRight) {
//            if (rStage != null)
//                return rStage;
//            else
//                return dependsOn.getChoice();
//        } else {
//            if (lStage != null)
//                return lStage;
//            else
//                return dependsOn.getChoice();
//        }
//    }
//
//    public boolean hasDecision() {
//        boolean result = isRight != null;
//        // in case this is a directory and all dependent conflicts have been solved choose an arbitrary side (right here)
//        if (!result && hasLeft() && hasRight()
//                && lStage.getIsDirectory() && rStage.getIsDirectory()
//                && !lStage.getDeleted() && !rStage.getDeleted()) {
//            chooseRight();
//            return true;
//        }
//        return result;
//    }
//
//    public String getKey() {
//        return key;
//    }
//
//    public Stage getLeft() {
//        if (lStageId == null)
//            return null;
//        return lStage;
//    }
//
//    public Stage getRight() {
//        if (rStageId == null)
//            return null;
//        return rStage;
//    }
//
//    @Override
//    public String toString() {
//        String left = lStage == null ? "null" : lStage.getName();
//        String right = rStage == null ? "null" : rStage.getName();
//        return "{class:\"" + getClass().getSimpleName() + "\",key:\"" + key + "\",isRight:\"" + isRight + "\", l:\"" + left + "\",r:\"" + right + "\" }";
//    }
//
//
//    public void chooseNothing() {
//        isRight = null;
//    }
//
//    public boolean isLeft() {
//        return isRight != null && !isRight;
//    }
//
//    public ConflictOlde dependOn(ConflictOlde dependsOn) {
//        this.dependsOn = dependsOn;
//        if (dependsOn != null) {
//            dependsOn.dependents.add(this);
//        }
//        return this;
//    }
//
//    public ConflictOlde getDependsOn() {
//        return dependsOn;
//    }
//
//    public Set<ConflictOlde> getDependents() {
//        return dependents;
//    }
//
//    public boolean hasLeft() {
//        return lStageId != null;
//    }
//
//    public boolean hasRight() {
//        return rStageId != null;
//    }
//
//    /**
//     * sorts, indents and adds empty rows
//     *
//     * @param conflicts
//     * @return
//     */
//    public static List<ConflictOlde> prepareConflicts(Collection<ConflictOlde> conflicts) {
//        List<ConflictOlde> result = new ArrayList<>();
//        List<ConflictOlde> rootConflicts = new ArrayList<>();
//        Set<ConflictOlde> withoutDepentents = new HashSet<>();
//        for (ConflictOlde conflict : conflicts) {
//            if (conflict.getDependsOn() == null)
//                rootConflicts.add(conflict);
//            else if (conflict.getDependents().size() == 0)
//                withoutDepentents.add(conflict);
//        }
//        for (ConflictOlde root : rootConflicts) {
//            result.add(root);
//            traversalAdding2(result, root.getDependents());
//            if (root.getDependents().size() > 0)
//                result.add(new EmptyRowConflict());
//        }
//        return result;
//    }
//
//    private static void traversalAdding2(List<ConflictOlde> result, Set<ConflictOlde> stuffToTraverse) {
//        for (ConflictOlde conflict : stuffToTraverse) {
//            result.add(conflict);
//            if (conflict.getDependents().size() > 0) {
//                traversalAdding2(result, conflict.getDependents());
//            }
//        }
//    }
//
////    /**
////     * sorts, indents and adds empty rows
////     *
////     * @param conflicts
////     * @return
////     */
////    public static List<ConflictOlde> prepareConflicts(Collection<ConflictOlde> conflicts) {
////        List<ConflictOlde> result = new ArrayList<>();
////        List<ConflictOlde> rootConflicts = getRootConflicts(conflicts);
////        for (ConflictOlde root : rootConflicts) {
////            result.add(root);
////            traversalAdding(result, root.getDependents());
////            if (root.getDependents().size() > 0)
////                result.add(new EmptyRowConflict());
////        }
////        return result;
////    }
////
////    private static void traversalAdding(List<ConflictOlde> result, Set<ConflictOlde> stuffToTraverse) {
////        for (ConflictOlde conflict : stuffToTraverse) {
////            result.add(conflict);
////            if (conflict.getDependents().size() > 0) {
////                traversalAdding(result, conflict.getDependents());
////            }
////        }
////    }
//
//    public ConflictOlde getDependentByName(String name) {
//        for (ConflictOlde dep : dependents) {
//            if (dep.getLeft() != null && dep.getLeft().getName().equals(name))
//                return dep;
//            if (dep.getRight() != null && dep.getRight().getName().equals(name))
//                return dep;
//        }
//        return null;
//    }
//
//    public static List<ConflictOlde> getRootConflicts(Collection<ConflictOlde> conflicts) {
//        List<ConflictOlde> rootConflicts = new ArrayList<>();
//        for (ConflictOlde conflict : conflicts) {
//            if (conflict.getDependsOn() == null)
//                rootConflicts.add(conflict);
//        }
//        return rootConflicts;
//    }
//}

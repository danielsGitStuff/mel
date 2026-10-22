package de.mel.filesync.service.sync;

import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.StageSet;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.sql.SqlQueriesException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges the local stage sets (what the indexer found, one set per run; source file system, staged) into one.
 * <ul>
 * <li>Pairs are found by path and name. The newer edit wins.</li>
 * <li>What the newer set deleted takes everything of the older sets below it along.</li>
 * <li>The older set is merged into the newer one, so the result keeps the place of the newest set: a set the indexer made meanwhile counts as newer.</li>
 * </ul>
 * The content hash of a directory is the one of its newest entry.
 */
public class LocalStageSetMerger {
    private final StageDao stageDao;

    public LocalStageSetMerger(StageDao stageDao) {
        this.stageDao = stageDao;
    }

    /**
     * @param stageSets oldest first
     * @return the one stage set that is left (the newest of them), null if there was none
     */
    public StageSet mergeAll(List<StageSet> stageSets) throws SqlQueriesException {
        if (stageSets.isEmpty())
            return null;
        StageSet result = stageSets.get(0);
        for (int i = 1; i < stageSets.size(); i++) {
            mergeInto(result, stageSets.get(i));
            result = stageSets.get(i);
        }
        return result;
    }

    /**
     * @return what an entry of a directory has as its path ("/a/b/" for the directory /a/b)
     */
    private static String pathBelow(Stage directory) {
        return directory.getPath() + directory.getName() + "/";
    }

    /**
     * @param path of an entry ("/a/b/")
     * @return true if the path is in one of the directories, or below
     */
    private static boolean below(String path, Set<String> directories) {
        for (int slash = path.indexOf('/', 1); slash >= 0; slash = path.indexOf('/', slash + 1))
            if (directories.contains(path.substring(0, slash + 1)))
                return true;
        return false;
    }

    /**
     * Puts what the older set has and the newer one does not into the newer one. The older set is gone afterwards.
     */
    private void mergeInto(StageSet older, StageSet newer) throws SqlQueriesException {
        Long newerId = newer.getId().v();
        List<Stage> newerStages = stageDao.getStagesByStageSetAsList(newerId);
        Set<String> newerEntries = new HashSet<>();
        Set<String> deletedDirectories = new HashSet<>();
        // the stage of each directory in the newer set, by the path of its entries
        Map<String, Long> directories = new HashMap<>();
        long order = 0;
        for (Stage stage : newerStages) {
            newerEntries.add(stage.getPath() + stage.getName());
            if (stage.getIsDirectory()) {
                directories.put(pathBelow(stage), stage.getId());
                if (stage.getDeleted())
                    deletedDirectories.add(pathBelow(stage));
            }
            order = Math.max(order, stage.getOrder() + 1);
        }

        List<Stage> olderStages = new ArrayList<>(stageDao.getStagesByStageSetAsList(older.getId().v()));
        // directories before what is in them
        olderStages.sort(Comparator.comparing(Stage::getDepth).thenComparing(Stage::getOrder));
        for (Stage stage : olderStages) {
            if (newerEntries.contains(stage.getPath() + stage.getName()))
                continue;
            if (below(stage.getPath(), deletedDirectories))
                continue;
            Stage copy = new Stage().mergeValuesFrom(stage)
                    .setSymLink(stage.getSymLink())
                    .setCreated(stage.getCreated())
                    .setStageSet(newerId)
                    .setOrder(order++)
                    .setMerged(false)
                    .setParentId(directories.get(stage.getPath()));
            stageDao.insert(copy);
            if (copy.getIsDirectory())
                directories.put(pathBelow(copy), copy.getId());
        }
        // entries of the newer set in a directory that came from the older one
        for (Stage stage : newerStages) {
            Long parentId = directories.get(stage.getPath());
            if (stage.getParentId() == null && parentId != null) {
                stage.setParentId(parentId);
                stageDao.update(stage);
            }
        }
        stageDao.deleteStageSet(older.getId().v());
        new BfsToDfsConverter(stageDao).convert(newer);
    }
}

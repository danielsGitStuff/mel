package de.mel.filesync.index;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.N;
import de.mel.auth.tools.Order;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.index.watchdog.FileWatcher;
import de.mel.filesync.sql.*;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.sql.Hash;
import de.mel.sql.Pair;
import de.mel.sql.SqlQueriesException;
import fun.with.promise.Promise;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * Created by xor on 5/18/17.
 */
@SuppressWarnings("Duplicates")
public abstract class AbstractIndexer extends DeferredRunnable {
    protected final FileSyncDatabaseManager databaseManager;
    protected final StageDao stageDao;
    protected final FsDao fsDao;
    private final String serviceName;
    private final int rootPathLength;
    protected StageSet initialStageSet;
    protected StageSet examinedStageSet;
    protected Long initialStageSetId;
    protected Long examinedStageSetId;
    private Order order = new Order();

    protected InitialIndexConflictHelper initialIndexConflictHelper;

    public void setInitialIndexConflictHelper(InitialIndexConflictHelper initialIndexConflictHelper) {
        this.initialIndexConflictHelper = initialIndexConflictHelper;
    }

    protected AbstractIndexer(FileSyncDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.stageDao = databaseManager.getStageDao();
        this.fsDao = databaseManager.getFsDao();
        this.serviceName = databaseManager.getMelFileSyncService().getRunnableName();
        this.rootPathLength = databaseManager.getFileSyncSettings().getRootDirectory().getPath().length();
    }

    @Override
    public Promise<Void, Void> onShutDown() {
        Lok.debug(getClass().getSimpleName() + "[" + initialStageSetId + "] for " + serviceName + ".onShutDown");
        return null;
    }

    protected void initStage(String stageSetType, Iterator<IFile> iterator, FileWatcher fileWatcher, long basedOnVersion) throws IOException, SqlQueriesException {
        initialStageSet = stageDao.createStageSet(stageSetType, null, null, null, basedOnVersion);
        if (initialIndexConflictHelper != null)
            initialIndexConflictHelper.onStart(initialStageSet);
        final int rootPathLength = databaseManager.getFileSyncSettings().getRootDirectory().getPath().length();
        this.initialStageSetId = initialStageSet.getId().v();

        while (iterator.hasNext()) {
            IFile f = iterator.next();
            String name = f.getAbsolutePath().length() > rootPathLength ? f.getName() : "";
            IFile parent = f.getParentFile();
            String path = parent.getAbsolutePath().length() >= rootPathLength ? parent.getAbsolutePath().substring(rootPathLength) + "/" : "";
            Stage stage = new Stage().setStageSet(initialStageSetId);
            stage.setSynced(true); // true because this is an observation on the file system.
            stage.setPath(path).setName(name);
            stage.setOrder(order.ord());
            if (stage.getDepthPair().isNull()) {
                IFile root = AbstractFile.instance(this.databaseManager.getFileSyncSettings().getRootDirectory().getPath());
                IFile fileUp = f;
                int depth = 0;
                while (!root.getAbsolutePath().equals(fileUp.getAbsolutePath()) && fileUp.getAbsolutePath().length() > root.getAbsolutePath().length()) {
                    fileUp = fileUp.getParentFile();
                    depth++;
                }
                stage.setDepth(depth);
            }
            if (stage.getDepthPair().equalsValue(0))
                stage.setFsId(this.databaseManager.melFileSyncService.getFileSyncSettings().getRootDirectory().getId());
            stageDao.insert(stage);
        }
    }

    /**
     * the stages of the examined stage set by path+name. Needed to hang stages below their parents and to stage nothing twice.
     */
    private Map<String, Stage> examinedStages;
    private Set<String> unchangedPaths;
    private Order examineOrder;

    private String rootPath() {
        return databaseManager.getFileSyncSettings().getRootDirectory().getPath();
    }

    private IFile fileOf(String path, String name) {
        return AbstractFile.instance(rootPath() + path + name);
    }

    private static boolean isRoot(String path, String name) {
        return path.isEmpty() && name.isEmpty();
    }

    /**
     * @return the stage of the directory that contains the file, null if there is none (root)
     */
    private Stage examineParent(String path) throws SqlQueriesException {
        // "/a/b/" -> "/a/" and "b"
        String trimmed = path.substring(0, path.length() - 1);
        int slash = trimmed.lastIndexOf('/');
        return examinePath(trimmed.substring(0, slash + 1), trimmed.substring(slash + 1));
    }

    /**
     * Platforms report file times with different precision (Android truncates, the fs table may hold what another platform reported).
     * Times that are in the same second are the same time.
     */
    private static boolean sameTime(Pair<Long> inFs, Long onDisk) {
        if (inFs.isNull() || onDisk == null)
            return inFs.isNull() && onDisk == null;
        return Math.floorDiv(inFs.v(), 1000L) == Math.floorDiv(onDisk, 1000L);
    }

    /**
     * A hint, not a proof: if it says "same", the file is not read. Only what every platform has and keeps is compared: modified time, inode and size.
     * The created time is not: Linux cannot set it (a transferred file has the time it was written, not the one of the sender) and Android has none.
     * What the hint lets pass is decided by the content hash afterwards ({@link #minimizeStageSet()}).
     */
    public static boolean sameAsInFs(GenericFSEntry fsEntry, FsBashDetails details, long size) {
        return !fsEntry.getIsDirectory().v()
                && sameTime(fsEntry.getModified(), details.getModified())
                && fsEntry.getiNode().equalsValue(details.getiNode())
                && fsEntry.getSize().equalsValue(size);
    }

    /**
     * Puts what is found at the path into the examined stage set, if it differs from what the fs table knows:
     * <ul>
     *     <li>gone: staged as deleted (only if the fs table knows it, and it was not just waiting for a transfer)</li>
     *     <li>a file that is not the same as in the fs table (or new)</li>
     *     <li>a directory, together with whatever in it is new or gone</li>
     * </ul>
     * Parents are examined first, so a stage hangs below the stage of its directory or, if that did not change, below the fs entry of it.
     *
     * @return the stage, or null if the path is unchanged
     */
    private Stage examinePath(String path, String name) throws SqlQueriesException {
        String key = path + name;
        if (examinedStages.containsKey(key))
            return examinedStages.get(key);
        if (unchangedPaths.contains(key))
            return null;
        IFile file = isRoot(path, name) ? AbstractFile.instance(rootPath()) : fileOf(path, name);
        GenericFSEntry fsEntry = fsDao.getGenericByPathAndName(path, name);
        Stage stage = new Stage().setStageSet(examinedStageSetId).setPath(path).setName(name);
        stage.setSynced(true); // true because this is an observation on the file system.
        if (!file.exists()) {
            if (fsEntry == null || (!fsEntry.getIsDirectory().v() && fsEntry.getSynced().notNull() && !fsEntry.getSynced().v())) {
                // never known, or a file we are still waiting for
                unchangedPaths.add(key);
                return null;
            }
            stage.setDeleted(true).setIsDirectory(fsEntry.getIsDirectory().v()).setContentHash(fsEntry.getContentHash().v());
        } else {
            boolean directory = file.isDirectory();
            FsBashDetails details = BashTools.Companion.getFsBashDetails(file);
            if (!directory && fsEntry != null && sameAsInFs(fsEntry, details, file.length())) {
                unchangedPaths.add(key);
                return null;
            }
            stage.setDeleted(false).setIsDirectory(directory);
            // NPE possible: a folder may have been deleted manually just now and the details are null. Then: fail, clean up, restart.
            stage.applyFsBashDetails(details);
            if (!directory)
                stage.setSize(file.length());
        }
        stage.setDepth(isRoot(path, name) ? 0 : path.length() - path.replace("/", "").length());
        if (fsEntry != null) {
            stage.setFsId(fsEntry.getId().v());
            stage.setFsParentId(fsEntry.getParentId().v());
        }
        if (!isRoot(path, name)) {
            Stage parent = examineParent(path);
            // the parent may have found us already
            if (examinedStages.containsKey(key))
                return examinedStages.get(key);
            if (parent != null) {
                stage.setParentId(parent.getId());
                if (parent.getFsId() != null)
                    stage.setFsParentId(parent.getFsId());
            } else {
                String trimmed = path.substring(0, path.length() - 1);
                int slash = trimmed.lastIndexOf('/');
                GenericFSEntry fsParent = fsDao.getGenericByPathAndName(trimmed.substring(0, slash + 1), trimmed.substring(slash + 1));
                if (fsParent != null)
                    stage.setFsParentId(fsParent.getId().v());
            }
        }
        stage.setOrder(examineOrder.ord());
        stageDao.insert(stage);
        examinedStages.put(key, stage);
        if (file.exists() && file.isDirectory())
            examineDirectory(stage, file, fsEntry != null && fsEntry.getIsDirectory().v() ? fsEntry : null);
        return stage;
    }

    /**
     * Sets the content hash of the directory stage (names of what is in it). If it is the one the fs table has nothing was added or removed.
     * Otherwise whatever is new or gone is examined as well, a new directory is examined down to its leaves.
     */
    private void examineDirectory(Stage stage, IFile dir, @Nullable GenericFSEntry fsDirectory) throws SqlQueriesException {
        Map<String, Boolean> onDisk = new HashMap<>(); // name -> is directory
        for (IFile f : dir.listDirectories())
            onDisk.put(f.getName(), true);
        for (IFile f : dir.listFiles())
            onDisk.put(f.getName(), false);
        boolean root = isRoot(stage.getPath(), stage.getName());
        if (root)
            onDisk.remove(FileSyncStrings.TRANSFER_DIR);
        Map<String, FsEntry> inFs = new HashMap<>();
        Set<String> names = new HashSet<>(onDisk.keySet());
        if (fsDirectory != null) {
            for (FsEntry fsContent : fsDao.getDirectoryContent(fsDirectory.getId().v())) {
                inFs.put(fsContent.getName().v(), fsContent);
                // add outstanding files
                if (!fsContent.getIsDirectory().v() && !fsContent.getSynced().v())
                    names.add(fsContent.getName().v());
            }
        }
        Set<String> subDirectories = new HashSet<>();
        Set<String> files = new HashSet<>();
        for (String name : names) {
            Boolean isDirectory = onDisk.get(name);
            if (isDirectory == null)
                isDirectory = false; // outstanding file
            (isDirectory ? subDirectories : files).add(name);
        }
        String contentHash = FsDirectory.calculateContentHash(subDirectories, files);
        stage.setContentHash(contentHash);
        stageDao.update(stage);
        if (fsDirectory != null && fsDirectory.getContentHash().equalsValue(contentHash))
            return; // nothing added, nothing removed
        String childPath = stage.getPath() + stage.getName() + "/";
        for (Map.Entry<String, Boolean> child : onDisk.entrySet()) {
            FsEntry known = inFs.get(child.getKey());
            // what is known and of the same kind is examined when it was reported
            if (known == null || !child.getValue().equals(known.getIsDirectory().v()))
                examinePath(childPath, child.getKey());
        }
        for (String name : inFs.keySet())
            if (!onDisk.containsKey(name))
                examinePath(childPath, name);
    }

    /**
     * Removes the stages that say the same as the fs table.
     */
    protected void minimizeStageSet() throws SqlQueriesException {
        for (Stage stage : stageDao.getStagesWithFsId(this.examinedStageSetId)) {
            GenericFSEntry fsEntry = fsDao.getGenericById(stage.getFsId());
            if (fsEntry.getIsDirectory().equalsValue(stage.getIsDirectory()) && fsEntry.getContentHash().equalsValue(stage.getContentHash()) && !Boolean.TRUE.equals(stage.getDeleted())) {
                // did not change
                boolean canRemove = true;
                List<Stage> children = stageDao.getStagesByParentId(stage.getId());
                for (Stage child : children) {
                    if (child.getFsIdPair().isNull() && child.getFsParentIdPair().isNull()) {
                        canRemove = false;
                        break;
                    }
                }
                if (canRemove) {
                    for (Stage child : children) {
                        child.getParentIdPair().nul();
                        stageDao.update(child);
                    }
                    stageDao.deleteStageById(stage.getId());
                }
            }
        }
    }

    /**
     * Compares what the initial stage set reports with the fs table and puts the difference in a new stage set (the examined one).
     * The initial stage set is deleted.
     */
    protected void examineStage() throws SqlQueriesException {
        examinedStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_FS, null, null, initialStageSet.getVersion().v(), initialStageSet.getBasedOnVersion().v());
        examinedStageSetId = examinedStageSet.getId().v();
        examinedStages = new HashMap<>();
        unchangedPaths = new HashSet<>();
        examineOrder = new Order();
        List<String[]> reported = new ArrayList<>();
        N.sqlResource(stageDao.getStagesByStageSet(initialStageSetId), stages -> {
            Stage reportedStage = stages.getNext();
            while (reportedStage != null) {
                reported.add(new String[]{reportedStage.getPath(), reportedStage.getName()});
                reportedStage = stages.getNext();
            }
        });
        for (String[] pathAndName : reported)
            examinePath(pathAndName[0], pathAndName[1]);
        examinedStages = null;
        unchangedPaths = null;
        stageDao.deleteStageSet(initialStageSetId);
        Lok.debug("examine done");
    }

    protected void hashFiles() throws SqlQueriesException {
        N.sqlResource(stageDao.getFilesWithoutHashAsResource(examinedStageSetId), stages -> {
            Stage stage = stages.getNext();
            while (stage != null) {
                // gone files have the hash of the fs table, so only files that exist
                IFile file = fileOf(stage.getPath(), stage.getName());
                stage.setContentHash(Hash.md5(file.inputStream()));
                stageDao.update(stage);
                stage = stages.getNext();
            }
        });
    }

    /**
     * Hashes the files, drops what did not change and marks the examined stage set as staged.
     * If there is nothing left the stage set is deleted.
     *
     * @return the id of the staged stage set, null if nothing changed
     */
    protected Long finishStaging() throws SqlQueriesException {
        hashFiles();
        minimizeStageSet();
        if (!stageDao.stageSetHasContent(examinedStageSetId)) {
            stageDao.deleteStageSet(examinedStageSetId);
            examinedStageSetId = null;
            return null;
        }
        examinedStageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(examinedStageSet);
        return examinedStageSetId;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + serviceName;
    }
}

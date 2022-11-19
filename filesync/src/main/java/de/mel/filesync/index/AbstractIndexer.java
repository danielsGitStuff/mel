package de.mel.filesync.index;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.Eva;
import de.mel.auth.tools.MapWrap;
import de.mel.auth.tools.N;
import de.mel.auth.tools.Order;
import de.mel.core.serialize.serialize.tools.OTimer;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.RootDirectory;
import de.mel.filesync.index.watchdog.FileWatcher;
import de.mel.filesync.sql.*;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.sql.Hash;
import de.mel.sql.RWLock;
import de.mel.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
    protected Boolean fastBooting = true;

    protected InitialIndexConflictHelper initialIndexConflictHelper;

    public void setInitialIndexConflictHelper(InitialIndexConflictHelper initialIndexConflictHelper) {
        this.initialIndexConflictHelper = initialIndexConflictHelper;
    }

    protected AbstractIndexer(FileSyncDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        fastBooting = databaseManager.getFileSyncSettings().getFastBoot();
        this.stageDao = databaseManager.getStageDao();
        this.fsDao = databaseManager.getFsDao();
        this.serviceName = databaseManager.getMelFileSyncService().getRunnableName();
        this.rootPathLength = databaseManager.getFileSyncSettings().getRootDirectory().getPath().length();
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        Lok.debug(getClass().getSimpleName() + "[" + initialStageSetId + "] for " + serviceName + ".onShutDown");
        return null;
    }

    protected String buildPathFromStage(Stage stage) throws SqlQueriesException {
        String res = "";
        if (stage.getFsParentId() != null) {
            FsDirectory fsParent = fsDao.getDirectoryById(stage.getFsParentId());
            if (fsParent.isRoot())
                return databaseManager.getFileSyncSettings().getRootDirectory().getPath() + File.separator + stage.getName();
            res = fsDao.getFileByFsFile(databaseManager.getFileSyncSettings().getRootDirectory(), fsParent).getAbsolutePath();
            res += File.separator + stage.getName();
        } else if (stage.getParentId() != null) {
            Stage parentStage = stageDao.getStageById(stage.getParentId());
            return buildPathFromStage(parentStage) + File.separator + stage.getName();
        } else if (stage.getFsId() != null) {
            FsDirectory fs = fsDao.getDirectoryById(stage.getFsId());
            if (fs.isRoot())
                return databaseManager.getFileSyncSettings().getRootDirectory().getPath();
            Lok.error("this method failed");
            res = "2";
        }
        return res;
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
     * Calculate contentHash based on all subdirectories and files found on the file system.
     * Then add all {@link FsEntry}s that have not been synced yet.
     * Depth First Search
     *
     * @param dir
     * @param fsEntry
     * @param order
     * @return
     * @throws SqlQueriesException
     */
    private void examineDirContentHash(@NotNull Stage stage, @NotNull IFile dir, @Nullable FsEntry fsEntry, MapWrap<Long, Long> oldIdNewId, Order order) throws SqlQueriesException {
        Set<String> subDirectories = new HashSet<>();
        Set<String> files = new HashSet<>();
        // add everything in the directory
        for (IFile f : dir.listDirectories()) {
            subDirectories.add(f.getName());
        }
        for (IFile f : dir.listFiles()) {
            files.add(f.getName());
        }
        // remove transfer dir
        if (stage.getPath().equals("") && stage.getName().equals(""))
            subDirectories.remove(FileSyncStrings.TRANSFER_DIR);
        // add outstanding files
        if (fsEntry != null) {
            List<FsEntry> fsContent = fsDao.getDirectoryContent(fsEntry.getId().v());
            for (FsEntry fsContentEntry : fsContent) {
                if (!fsContentEntry.getIsDirectory().v() && !fsContentEntry.getSynced().v()) {
                    files.add(fsContentEntry.getName().v());
                }
            }
        }
        String contentHash = FsDirectory.calculateContentHash(subDirectories, files);
        // content hash did not change!
        if (fsEntry != null && fsEntry.getContentHash().equalsValue(contentHash)) {
            return;
        } else {
            // content hash differs or directory is new
            stage.setContentHash(contentHash);
            FsBashDetails details = BashTools.Companion.getFsBashDetails(dir);
            // this method is only called for directories that exist. It might happen though, that a folder was deleted manually at this point and details returns null.
            // In this case: fail, clean up, restart. So the NPE that may pop up here serves a purpose.
            stage.applyFsBashDetails(details);
            stageDao.update(stage);
            for (IFile subDirectory : dir.listDirectories()) {
                // check for transfer dir and skip
                if (stage.getName().equals("") && subDirectory.getName().equals(FileSyncStrings.TRANSFER_DIR))
                    continue;
                String path = subDirectory.getParentFile().getAbsolutePath().substring(rootPathLength) + "/";
                Stage existingStage = stageDao.getStageByPathAndName(initialStageSetId, path, subDirectory.getName());
                Stage newStage;
                if (existingStage == null) {
                    newStage = new Stage();
                    newStage.setName(subDirectory.getName());
                    newStage.setPath(path);
                } else {
                    newStage = existingStage.copy();
                }

                newStage.setStageSet(examinedStageSetId);
                newStage.setDepth(stage.getDepth() + 1);
                newStage.setSynced(true);
                newStage.setParentId(stage.getId());
                newStage.setOrder(this.order.ord());
                newStage.setIsDirectory(true);
                newStage.setDeleted(false);
                FsBashDetails subDetails = BashTools.Companion.getFsBashDetails(subDirectory);
                // NPE possible, see above
                newStage.applyFsBashDetails(subDetails);
                newStage.setOrder(order.ord());

                if (fsEntry != null) {
                    newStage.setFsParentId(fsEntry.getId().v());
                    newStage.setDepth(fsEntry.getDepth().v());
                }

                stageDao.insert(newStage);
                if (existingStage != null)
                    oldIdNewId.put(existingStage.getId(), newStage.getId());
                FsEntry subFsEntry = fsDao.getGenericByPathAndName(path, subDirectory.getName());
                // DFS
                examineDirContentHash(newStage, subDirectory, subFsEntry, oldIdNewId, order);
            }
            for (IFile file : dir.listFiles()) {
                String path = file.getParentFile().getAbsolutePath().substring(rootPathLength) + "/";
                Stage existingStage = stageDao.getStageByPathAndName(initialStageSetId, path, file.getName());
                Stage newStage;
                if (existingStage == null) {
                    newStage = new Stage();
                    newStage.setName(file.getName());
                    newStage.setPath(path);
                } else {
                    newStage = existingStage.copy();
                }
                newStage.setStageSet(examinedStageSetId);
                newStage.setDepth(stage.getDepth() + 1);
                newStage.setSynced(true);
                newStage.setParentId(stage.getId());
                newStage.setOrder(this.order.ord());
                newStage.setIsDirectory(false);
                newStage.setDeleted(false);
                newStage.setSize(file.length());
                FsBashDetails fileDetails = BashTools.Companion.getFsBashDetails(file);
                // NPE possible, see above
                newStage.applyFsBashDetails(fileDetails);
                newStage.setOrder(order.ord());
                // old entry found. copy contentHash if modified, iNode or created changed
                if (fsEntry != null && !fsEntry.getIsDirectory().v()) {
                    newStage.setFsParentId(fsEntry.getId().v());
                    if (fsEntry.getCreated().equalsValue(fileDetails.getCreated()) && fsEntry.getModified().equalsValue(fileDetails.getModified()) && fsEntry.getiNode().equalsValue(fileDetails.getiNode())) {
                        newStage.setContentHash(fsEntry.getContentHash().v());
                    }
                }
                stageDao.insert(newStage);
                if (existingStage != null)
                    oldIdNewId.put(existingStage.getId(), newStage.getId());
            }
        }
    }

    private void examineDir(@NotNull IFile iFile, @NotNull Stage stage, @Nullable FsEntry fsEntry, MapWrap<Long, Long> oldIdNewId, Order order) throws SqlQueriesException {
        if (iFile.exists()) {
            if (iFile.isDirectory()) {
                stage.setIsDirectory(true);
                IFile dir = iFile;
                this.examineDirContentHash(stage, dir, fsEntry, oldIdNewId, order);
            } else {
                stage.setIsDirectory(false);
            }
        } else {
            stage.setDeleted(true);
            // todo recursive delete
        }
        stageDao.update(stage);
    }

    protected void minimizeStageSet() throws SqlQueriesException {
        Lok.debug("asd");
        for (Stage stage : stageDao.getStagesWithFsId(this.examinedStageSetId)) {
            GenericFSEntry fsEntry = fsDao.getGenericById(stage.getFsId());
            if (fsEntry.getIsDirectory().equalsValue(stage.getIsDirectory()) && fsEntry.getContentHash().equalsValue(stage.getContentHash())) {
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

    protected void examineStage() throws SqlQueriesException {
        examinedStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_FS, null, null, initialStageSet.getVersion().v(), initialStageSet.getBasedOnVersion().v());
        examinedStageSetId = examinedStageSet.getId().v();
        Order order = new Order();
        N.sqlResource(stageDao.getStagesByStageSet(initialStageSetId), stages -> {
            MapWrap<Long, Long> oldNewIdMap = new MapWrap<>(new HashMap<>());
            Stage oldStage = stages.getNext();
            while (oldStage != null) {
                Stage stage = oldStage.copy();
                stage.setStageSet(examinedStageSetId);
                String name = stage.getName();
                String path = databaseManager.getFileSyncSettings().getRootDirectory().getPath() + stage.getPath();
                IFile f = path.length() > 0 ? AbstractFile.instance(path + name) : AbstractFile.instance(databaseManager.getFileSyncSettings().getRootDirectory().getPath());
                IFile fParent = path.length() == 0 ? null : f.getParentFile();
                String parentPath = fParent == null || fParent.getAbsolutePath().length() < rootPathLength ? null : fParent.getAbsolutePath().substring(rootPathLength);
                parentPath += "/";
                Stage existingStage = stageDao.getStageByPathAndName(examinedStageSetId, parentPath, name);
                if (existingStage != null) {
                    oldStage = stages.getNext();
                    continue;
                }
                FsEntry fsEntry = fsDao.getGenericByPathAndName(path, name);
                FsDirectory fsParent = fParent == null ? null : fsDao.getFsDirectoryByPathAndName(parentPath, fParent.getName());
                if (fsEntry != null) {
                    stage.setFsId(fsEntry.getId().v());
                    stage.setFsParentId(fsEntry.getParentId().v());
                    stage.setDepth(fsEntry.getDepth().v());
                }
                if (fsParent != null) {
                    stage.setDepth(fsParent.getDepth().v() + 1);
                    stage.setFsParentId(fsParent.getId().v());
                }
                if (oldStage.getParentId() != null && oldNewIdMap.containsKey(oldStage.getParentId())) {
                    stage.setParentId(oldNewIdMap.get(oldStage.getParentId()));
                }
                stage.setOrder(order.ord());
                stageDao.insert(stage);
                oldNewIdMap.put(oldStage.getId(), stage.getId());

                examineDir(f, stage, fsEntry, oldNewIdMap, order);
                oldStage = stages.getNext();
            }
        });
        stageDao.deleteStageSet(initialStageSetId);
        Lok.debug("examine done");
    }

    protected void hashFiles() throws SqlQueriesException {
        N.sqlResource(stageDao.getFilesWithoutHashAsResource(examinedStageSetId), stages -> {
            Stage stage = stages.getNext();
            while (stage != null) {
                IFile file = AbstractFile.instance(this.databaseManager.getFileSyncSettings().getRootDirectory().getPath() + stage.getPath() + stage.getName());
                stage.setContentHash(Hash.md5(file.inputStream()));
                stageDao.update(stage);
                stage = stages.getNext();
            }
        });
    }

    /**
     * Compresses the {@link StageSet} to a delta of the current {@link FsEntry}s.
     * If there is no difference and the {@link StageSet} is empty it is deleted.
     *
     * @throws SqlQueriesException
     * @throws IOException
     */
    protected void examineStageOlde() throws SqlQueriesException, IOException {
        OTimer timer = new OTimer("examine all");
        OTimer timer1 = new OTimer("examine 1");
        OTimer timer2 = new OTimer("examine 2");
        OTimer timer3 = new OTimer("examine 2");
        OTimer timerUpdate1 = new OTimer("updateFileStage.internal.1");
        OTimer timerUpdate2 = new OTimer("updateFileStage.internal.2");
        timer.start();
        N.sqlResource(stageDao.getStagesByStageSet(initialStageSetId), stages -> {
            Stage stage = stages.getNext();
            while (stage != null) {
                // build a File
                timer1.start();
                String path = buildPathFromStage(stage);
                IFile f = AbstractFile.instance(path);

                if (!f.exists()) {
                    continue;
                }

                timer1.stop();
                if (f.isDirectory()) {
                    stage.setIsDirectory(true);
                    timer2.start();
                    roamDirectoryStage(stage, f);
                    timer2.stop();
                } else {
                    stage.setIsDirectory(false);
                    timer3.start();
                    this.updateFileStage(stage, f, timerUpdate1, timerUpdate2, null);
                    timer3.stop();
                }
                stage = stages.getNext();
            }
        });
        stageDao.deleteIdenticalToFs(initialStageSetId);
        Lok.debug("StageIndexerRunnable.runTry(" + initialStageSetId + ").finished");
        initialStageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED);

        stageDao.updateStageSet(initialStageSet);
        if (!stageDao.stageSetHasContent(initialStageSetId)) {
            stageDao.deleteStageSet(initialStageSetId);
            initialStageSetId = null;
        }
        timer.stop().print();
        timer1.print();
        timer2.print();
        timer3.print();
        timerUpdate1.print();
        timerUpdate2.print();
    }


    protected void initStageOlde(String stageSetType, Iterator<IFile> iterator, FileWatcher fileWatcher, long basedOnVersion) throws IOException, SqlQueriesException {
        OTimer timer = new OTimer("initStage().connect2fs");
        OTimer timerInternal1 = new OTimer("initStage.internal.1");
        OTimer timerInternal2 = new OTimer("initStage.internal.2");
        OTimer timerInternal3 = new OTimer("initStage.internal.3");


        initialStageSet = stageDao.createStageSet(stageSetType, null, null, null, basedOnVersion);
        if (initialIndexConflictHelper != null)
            initialIndexConflictHelper.onStart(initialStageSet);
        final int rootPathLength = databaseManager.getFileSyncSettings().getRootDirectory().getPath().length();
        this.initialStageSetId = initialStageSet.getId().v();


        IndexHelper indexHelper = new IndexHelper(databaseManager, initialStageSetId, order);
        while (iterator.hasNext()) {
            IFile f = iterator.next();
            IFile parent = f.getParentFile();

            FsDirectory fsParent = null;
            FsEntry fsEntry = null;
            Stage stage;

            timerInternal1.start();

            try {
                if (BashTools.Companion.isSymLink(f)) {
                    if (!databaseManager.getFileSyncSettings().getUseSymLinks())
                        continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // find the actual relating FsEntry of the parent directory
            if (parent != null && parent.getAbsolutePath().length() >= rootPathLength) {
                fsParent = fsDao.getFsDirectoryByPathOlde(parent);
            }
            // find its relating FsEntry
            if (fsParent != null) {
                GenericFSEntry genParentDummy = new GenericFSEntry();
                genParentDummy.getParentId().v(fsParent.getId());
                genParentDummy.getName().v(f.getName());
                GenericFSEntry gen = fsDao.getGenericFileByName(genParentDummy);
                if (gen != null) {
                    fsEntry = gen.ins();
                }
            }
            // still finding.. might be root dir
            if (fsEntry == null && f.isDirectory()) {
                fsEntry = fsDao.getFsDirectoryByPathOlde(f);
            }
            timerInternal1.stop();
            //file might been deleted yet :(
            if (!f.exists() && fsEntry == null)
                continue;
            // stage actual File
            stage = new Stage().setName(f.getName());//.setIsDirectory(f.isDirectory());
            String path = f.getAbsolutePath().substring(rootPathLength);
            if (path.length() > f.getName().length()) {
                path = path.substring(0, path.length() - f.getName().length());
            }
            stage.setPath(path);
            if (fsEntry != null) {
                stage.setFsId(fsEntry.getId().v())
                        .setFsParentId(fsEntry.getParentId().v())
                        .setDepth(fsEntry.getDepth().v());
                //check for fastboot
                indexHelper.fastBoot(f, fsEntry, stage);
            }
            if (fsParent != null) {
                stage.setFsParentId(fsParent.getId().v());
                stage.setDepth(fsParent.getDepth().v() + 1);
            }
            // we found everything which already exists in das datenbank

            timer.start();
            Stage stageParent = indexHelper.connectToFs(parent);
            if (stageParent != null) {
                stage.setParentId(stageParent.getId());
                stage.setDepth(stageParent.getDepth() + 1);
            }
            timer.stop();
            timerInternal2.start();
            if (stageParent != null)
                stage.setParentId(stageParent.getId());
            if (fsParent != null) {
                stage.setFsParentId(fsParent.getId().v());
            }
            if (fsEntry != null) {
                stage.setFsId(fsEntry.getId().v());
//                stage.setVersion(fsEntry.getVersion().v());
                stage.setName(fsEntry.getName().v());
            }
            stage.setStageSet(initialStageSet.getId().v());
            stage.setDeleted(!f.exists());
            if (f.isDirectory()) {
                fileWatcher.watchDirectory(f);
            }

            stage.setOrder(order.ord());
            if (stage.getDepthPair().isNull()) {
                timerInternal3.start();
                int depth = 0;
                IFile file = f;
                while (rootPathLength > file.getAbsolutePath().length()) {
                    file = file.getParentFile();
                    depth++;
                }
                stage.setDepth(depth);
                timerInternal3.stop();
            }
            stageDao.insert(stage);
            timerInternal2.stop();

        }
        // done here. set the indexer to work

        timer.print();
        timerInternal1.print();
        timerInternal2.print();
        timerInternal3.print();
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + serviceName;
    }

    private void roamDirectoryStage(Stage stage, IFile stageFile) throws SqlQueriesException, IOException, InterruptedException {
        if (stage.getIsDirectory() && stage.getDeleted())
            return;
        //todo weiter hier"
        FsBashDetails fsBashDetails = BashTools.Companion.getFsBashDetails(stageFile);

        // skip if drive ignores symlinks
        if (fsBashDetails.isSymLink()) {
            if (databaseManager.getFileSyncSettings().getUseSymLinks()) {
                if (isValidSymLink(stageFile, fsBashDetails)) {
                    stage.setSymLink(fsBashDetails.getSymLinkTarget())
                            .setContentHash("0")
                            .setModified(fsBashDetails.getModified())
                            .setiNode(fsBashDetails.getiNode());
                    // this is a symlink now but we have to delete whatever was in there before
                    if (stage.getFsIdPair().notNull()) {
                        List<GenericFSEntry> content = fsDao.getContentByFsDirectory(stage.getFsId());
                        for (GenericFSEntry entry : content) {
                            Stage delStage = GenericFSEntry.generic2Stage(entry, initialStageSetId);
                            delStage.setParentId(stage.getId());
                            delStage.setDeleted(true);
                            delStage.setOrder(order.ord());
                            stageDao.insert(delStage);
                        }
                    }
                    stageDao.update(stage);
                    return;

                } else {
                    return;
                }
            } else {
                return;
            }
        }
        Eva.runIf(() -> this.databaseManager.melFileSyncService.getFileSyncSettings().isServer() && stage.getName().equals("sub1"), () -> {
            Lok.debug("debug content hash 211");
        });

        FsDirectory newFsDirectory = new FsDirectory();
        // roam directory if necessary
        IFile[] files = stageFile.listFiles();
        IFile[] subDirs = stageFile.listDirectories();
        Map<String, FsBashDetails> bashDetailsMap = BashTools.Companion.getContentFsBashDetails(stageFile);


        // map will contain all FsEntry that must be deleted
        Map<String, GenericFSEntry> stuffToDelete = new HashMap<>();
        if (stage.getFsId() != null) {
            List<GenericFSEntry> generics = fsDao.getContentByFsDirectory(stage.getFsId());
            for (GenericFSEntry gen : generics) {
                if (gen.getSynced().v())
                    stuffToDelete.put(gen.getName().v(), gen);
            }
        }
        // remove deleted stuff first (because of the order)
        if (files != null) {
            for (IFile subFile : files) {
                stuffToDelete.remove(subFile.getName());
                // check if file is supposed to be here.
                // it just might not be transferred yet.
//                FsFile fsFile = fsDao.getFsFileByFile(subFile);
//                if (fsFile != null && fsFile.getSynced().v())
//                    stuffToDelete.remove(subFile.getName());
            }
        }
        if (subDirs != null) {
            for (IFile subDir : subDirs) {
                stuffToDelete.remove(subDir.getName());
            }
        }
        for (String name : stuffToDelete.keySet()) {
            GenericFSEntry gen = stuffToDelete.get(name);
            Stage alreadyOnStage = stageDao.getStageByFsId(gen.getId().v(), initialStageSetId);
            if (alreadyOnStage == null) {
                Stage delStage = GenericFSEntry.generic2Stage(gen, initialStageSetId)
                        .setDeleted(true)
                        .setOrder(order.ord())
//                        .setVersion(gen.getVersion().v())
                        .setiNode(gen.getiNode().v())
                        .setModified(gen.getModified().v())
//                        .setSynced(gen.getSynced().v())
                        .setSynced(true)
                        .setParentId(stage.getId());
                stageDao.insert(delStage);
            } else {
                alreadyOnStage.setContentHash(gen.getContentHash().v());
                alreadyOnStage.setiNode(gen.getiNode().v());
                stage.setSize(gen.getSize().v());
                stage.setModified(gen.getModified().v());
                stageDao.update(alreadyOnStage);
            }
        }
        // this speeds up things dramatically
        final Map<String, Stage> contentMap = new HashMap<>();
        {
            List<Stage> content = stageDao.getStageContent(stage.getId());
            N.forEach(content, stage1 -> contentMap.put(stage1.getName(), stage1));
        }

        if (files != null) {
            for (IFile subFile : files) {
                // check if which subFiles are on stage or fs. if not, index them

                Stage subStage = contentMap.get(subFile.getName());
                FsFile subFsFile = fsDao.getFsFileByName(stage.getFsId(), subFile.getName());

                if (subStage == null && subFsFile == null) {
                    // stage
                    subStage = new Stage().setName(subFile.getName())
                            .setIsDirectory(false)
                            .setParentId(stage.getId())
                            .setFsParentId(stage.getFsId())
                            .setStageSet(initialStageSetId)
                            .setDeleted(false)
                            .setOrder(order.ord());
//                            .setRelativePath(subFile.getAbsolutePath().substring(rootPathLength));

                    FsBashDetails subFsBashDetails = bashDetailsMap.get(subFile.getName());

                    if (subFsBashDetails.isSymLink()) {
                        if (databaseManager.getFileSyncSettings().getUseSymLinks()) {
                            if (isValidSymLink(subFile, subFsBashDetails)) {
                                subStage.setSymLink(subFsBashDetails.getSymLinkTarget());
                            }
                        } else {
                            //ignore
                            continue;
                        }
                    }
                    newFsDirectory.addFile(new FsFile(subFile));
                    stageDao.insert(subStage);
                    this.updateFileStage(subStage, subFile, null, null, subFsBashDetails);
//                    subStage.setOrder(order.ord());
                } else if (subStage != null && !subFile.isDirectory()) {
                    // do not forget to add this!
                    newFsDirectory.addFile(new FsFile(subFile));
                    //update stage
                    subStage.setIsDirectory(false);
                    stageDao.update(subStage);
                }
            }
        }
        if (subDirs != null)
            for (
                    IFile subDir : subDirs) {
                if (subDir.getAbsolutePath().equals(databaseManager.getFileSyncSettings().getTransferDirectory().getAbsolutePath()))
                    continue;
                //                Stage subStage = stageDao.getStageByStageSetParentName(stageSetId, stage.getId(), subDir.getName());
                Stage subStage = contentMap.get(subDir.getName());
                FsBashDetails subDirDetails = bashDetailsMap.get(subDir.getName()); //BashTools.Companion.getFsBashDetails(subDir);
                stuffToDelete.remove(subDir.getName());
                if (subDirDetails == null)
                    Lok.debug("could not find bashdetails for: " + subDir.getAbsolutePath());

                // if symlink, remove everything further down in the db file tree and skip the rest
                if (subDirDetails.isSymLink()) {
                    if (!databaseManager.getFileSyncSettings().getUseSymLinks()) {

//                            stageDao.deleteStageById(subStage.getId());
                        continue;
                    }
                    if (isValidSymLink(subDir, subDirDetails)) {
                        Lok.debug("debug");
                    } else {

//                            stageDao.deleteStageById(subStage.getId());
                    }
                    continue;
                }
                if (subDirDetails.isSymLink() && !databaseManager.getFileSyncSettings().getUseSymLinks())
                    continue;
                // if subDir is on stage or fs we don't have to roam it

                FsDirectory leSubDirectory = null;
                if (stage.getFsId() != null)
                    leSubDirectory = fsDao.getSubDirectoryByName(stage.getFsId(), subDir.getName());
                if (subStage == null && leSubDirectory == null) {
                    // roam
                    subStage = new Stage().setStageSet(initialStageSetId)
                            .setParentId(stage.getId())
                            .setFsParentId(stage.getFsId())
                            .setName(subDir.getName())
                            .setIsDirectory(true)
                            .setDeleted(!subDir.exists());
                    Lok.debug("StageIndexerRunnable[" + initialStageSetId + "].roamDirectoryStage.roam sub: " + subDir.getAbsolutePath());
                    subStage.setOrder(order.ord());
                    try {
                        stageDao.insert(subStage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    roamDirectoryStage(subStage, subDir);
                }
                if (leSubDirectory == null)
                    leSubDirectory = new FsDirectory(subDir);
                newFsDirectory.addSubDirectory(leSubDirectory);
            }
        // add not yet synced files to newStage
        if (stage.getFsId() != null) {
            List<FsFile> notSyncedFiles = fsDao.getNonSyncedFilesByFsDirectory(stage.getFsId());
            for (FsFile notSyncedFile : notSyncedFiles) {
                newFsDirectory.addFile(notSyncedFile);
            }
        }

        // save to stage
        newFsDirectory.calcContentHash();
        stage.setContentHash(newFsDirectory.getContentHash().

                v());
        RWLock waitLock = new RWLock().lockWrite();
        stage.setModified(fsBashDetails.getModified())
                        .

                setiNode(fsBashDetails.getiNode());
        if (fsBashDetails.isSymLink())
            stage.setSymLink(fsBashDetails.getSymLinkTarget());
        if (stage.getFsId() != null) {
            FsDirectory oldFsDirectory = fsDao.getFsDirectoryById(stage.getFsId());
            stageDao.update(stage);
        } else
            stageDao.update(stage);
        waitLock.unlockWrite();
        waitLock.lockWrite();
    }

    /**
     * checks whether file is a symlink, is within the root dir
     *
     * @param file
     * @param fsBashDetails
     * @return
     */
    private boolean isValidSymLink(IFile file, FsBashDetails fsBashDetails) throws IOException {
        if (file.getName().equals("subb"))
            Lok.debug("debug");
        if (!fsBashDetails.isSymLink())
            return false;
        final String targetPath = fsBashDetails.getSymLinkTarget();
        final String rootPath = databaseManager.getFileSyncSettings().getRootDirectory().getPath();
        IFile parent = file.getParentFile();
        final String path = N.result(() -> {
            if (targetPath.startsWith(File.separator))
                return targetPath;
            try {
                return parent.getCanonicalPath() + File.separator + targetPath;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "NEIN!";
        });
        if (path.length() <= rootPath.length())
            return false;
        if (!path.startsWith(rootPath))
            return false;
        // check if is inside root dir
        File f = new File(path);
        String canPath = f.getCanonicalPath();
        return canPath.length() >= rootPath.length() && canPath.startsWith(rootPath);
//        return path.startsWith(rootPath);
    }

    private void updateFileStage(Stage stage, IFile stageFile, OTimer timer1, OTimer timer2, FsBashDetails fsBashDetails) throws IOException, SqlQueriesException, InterruptedException {
        // skip hashing if information is complete & fastBoot is enabled-> speeds up booting
        if (stageFile.exists()) {
            if (timer1 != null)
                timer1.start();
            // might have been set before. obey
            if (stage.getDeletedPair().equalsValue(true))
                return;


            FsEntry fsEntry = null;
            if (stage.getFsIdPair().notNull())
                fsEntry = fsDao.getFile(stage.getFsId());
            if (!fastBooting
                    || stage.getContentHashPair().isNull()
                    || stage.getiNodePair().isNull()
                    || stage.getModifiedPair().isNull()
                    || stage.getSizePair().isNull()) {

                if (fsBashDetails == null)
                    fsBashDetails = BashTools.Companion.getFsBashDetails(stageFile);
                if (fsBashDetails.isSymLink()) {
                    stage.setSymLink(fsBashDetails.getSymLinkTarget());
                    stage.setContentHash("0");
                } else {
                    stage.setSize(stageFile.length());
                    stage.setModified(fsBashDetails.getModified());
//                    stage.setCreated(fsBashDetails.getCreated());
                    stage.setiNode(fsBashDetails.getiNode());
                    if (fastBooting && fsEntry != null
                            && fsEntry.getModified().equalsValue(fsBashDetails.getModified())
                            && fsEntry.getiNode().equalsValue(fsBashDetails.getiNode())) {
                        // assume that nothing changed
                        stage.setContentHash(fsEntry.getContentHash().v());
                    } else {
                        stage.setContentHash(Hash.md5(stageFile.inputStream()));
                    }
                }
                stage.setiNode(fsBashDetails.getiNode());
                stage.setModified(fsBashDetails.getModified());
                stage.setCreated(fsBashDetails.getCreated());
            }

            if (timer1 != null)
                timer1.stop();
            if (timer2 != null)
                timer2.start();

            // stage can be deleted if nothing changed
            if (fsEntry != null) {
                if (fsEntry.getContentHash().v().equals(stage.getContentHash())
                        && fsEntry.getiNode().equalsValue(stage.getiNode())
                        && fsEntry.getModified().equalsValue(stage.getModified())
                        && fsEntry.getSize().equalsValue(stage.getSize())) {
//                    Lok.debug();
//                    stageDao.deleteStageById(stage.getId());
                } else {
                    if (stage.isSymLink() && !databaseManager.getFileSyncSettings().getUseSymLinks()) {
//                        Lok.debug();
//                        stageDao.deleteStageById(stage.getId());
                    } else {
                        stageDao.update(stage);
                    }
                }
            } else {
                if (stage.isSymLink() && !databaseManager.getFileSyncSettings().getUseSymLinks()) {
                    Lok.debug();
//                    stageDao.deleteStageById(stage.getId());
                } else {
                    stageDao.update(stage);
                }
            }
            if (timer2 != null)
                timer2.stop();
            if (initialIndexConflictHelper != null)
                initialIndexConflictHelper.check(fsEntry, stage);

        } else {
            stage.setDeleted(true);
            stageDao.update(stage);
        }
//        }
    }
}

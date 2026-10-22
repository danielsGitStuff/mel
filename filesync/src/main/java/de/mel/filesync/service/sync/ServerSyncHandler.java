package de.mel.filesync.service.sync;

import de.mel.Lok;
import de.mel.auth.file.IFile;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.MapWrap;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.auth.tools.lock2.P;
import de.mel.core.serialize.exceptions.MelJsonException;
import de.mel.filesync.data.*;
import de.mel.filesync.quota.OutOfSpaceException;
import de.mel.filesync.service.MelFileSyncService;
import de.mel.filesync.sql.*;
import de.mel.filesync.tasks.AvailHashEntry;
import de.mel.filesync.tasks.AvailableHashesContainer;
import de.mel.sql.ISQLResource;
import de.mel.sql.SqlQueriesException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 1/26/17.
 */
public class ServerSyncHandler extends SyncHandler {
    private HashAvailTimer hashAvailTimer = new HashAvailTimer(() -> {
        AvailableHashes hashesAvailable = ServerSyncHandler.this.hashAvailTimer.getHashesAvailableCopy();
        hashesAvailable.setIntent(FileSyncStrings.INTENT_HASH_AVAILABLE);
        N.forEachIgnorantly(fileSyncSettings.getServerSettings().getClients(),
                clientData -> melAuthService.connect(clientData.getCertId()).onDone(
                        mvp -> N.r(() -> mvp.message(clientData.getServiceUuid(), hashesAvailable))));
    });

    public ServerSyncHandler(MelAuthService melAuthService, MelFileSyncService melFileSyncService) {
        super(melAuthService, melFileSyncService);
    }

    /**
     * Commits of clients are handled one after another on a thread of their own: the service worker stays free for messages and transfer requests while
     * a commit is put in place. Versions change under the lock of the fs table, so also in turn with the server's own index (it commits on its thread).
     */
    private final java.util.concurrent.ExecutorService commitExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "commits " + melAuthService.getName());
        thread.setDaemon(true);
        return thread;
    });

    /**
     * handles the commit on the commit thread, returns at once
     */
    public void queueCommit(Request request) {
        commitExecutor.execute(() -> {
            try {
                handleCommit(request);
            } catch (Exception e) {
                Lok.error("commit failed: " + e);
                e.printStackTrace();
                if (request.isPending())
                    request.resolveError(e);
            }
        });
    }

    @Override
    public void onShutDown() {
        commitExecutor.shutdownNow();
        super.onShutDown();
    }

    @Override
    protected void setupTransferAvailable(DbTransferDetails details, StageSet stageSet, Stage stage) {
        if (stageSet.getSource().equalsValue(FileSyncStrings.STAGESET_SOURCE_CLIENT))
            details.getAvailable().v(true);
    }

    private boolean canCommit(Request request, Commit commit) throws SqlQueriesException {
        final long olderVersion = fileSyncDatabaseManager.getLatestVersion();
        if (commit.getBasedOnVersion() != olderVersion) {
            request.resolveError(new TooOldVersionException("old version: " + commit.getBasedOnVersion() + " vs " + fileSyncDatabaseManager.getLatestVersion(), fileSyncDatabaseManager.getLatestVersion()));
            return false;
        }
        return true;
    }

    protected void executeCommit(Request request, Commit commit, BunchOfLocks bunchOfLocks) throws SqlQueriesException {
        // stage everything
        StageSet stageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_CLIENT, request.getPartnerCertificate().getId().v(), commit.getServiceUuid(), null, commit.getBasedOnVersion());
        Map<Long, Long> oldStageIdStageIdMap = new HashMap<>();
        Iterator<Stage> iterator = commit.iterator();
        while (iterator.hasNext()) {
            Stage stage = iterator.next();
            stage.setStageSet(stageSet.getId().v());
            if (stage.getParentId() != null && oldStageIdStageIdMap.containsKey(stage.getParentId())) {
                stage.setParentId(oldStageIdStageIdMap.get(stage.getParentId()));
            }
            // the client knows an entry this server has deleted meanwhile (the user kept the client's change in a conflict): it is a new entry here
            if (stage.getFsIdPair().notNull() && this.fsDao.getFsEntryById(stage.getFsId()) == null)
                stage.setFsId(null);
            if (stage.getFsParentIdPair().notNull() && this.fsDao.getFsEntryById(stage.getFsParentId()) == null)
                stage.setFsParentId(null);
            if (stage.getFsIdPair().notNull()) {
                FsEntry fsEntry = this.fsDao.getFsEntryById(stage.getFsId());
                stage.setDepth(fsEntry.getDepth().v());
                stage.setPath(fsEntry.getPath().v());
            } else if (stage.getFsParentIdPair().notNull()) {
                FsEntry fsParent = this.fsDao.getFsEntryById(stage.getFsParentId());
                stage.setDepth(fsParent.getDepth().v() + 1);
                stage.setPath(fsParent.getPath().v() + fsParent.getName() + "/");
            } else if (stage.getParentId() != null) {
                // new below a directory that is new as well: it came with this commit and is staged already
                Stage parentStage = stageDao.getStageById(stage.getParentId());
                stage.setDepth(parentStage.getDepth() + 1);
                stage.setPath(parentStage.getPath() + parentStage.getName() + "/");
            }
            Long oldId = stage.getId();
            stageDao.insert(stage);
            oldStageIdStageIdMap.put(oldId, stage.getId());
        }
        commit.cleanUp();
        //TODO check goes here

        //map old stage ids with new fs ids
        //fsDao.lockWrite();
        Long oldVersion = fsDao.getLatestVersion();
        Map<Long, Long> stageIdFsIdMap = new MapWrap(new HashMap<>());

        try {
            this.commitStage(stageSet.getId().v(), bunchOfLocks, stageIdFsIdMap);
        } catch (OutOfSpaceException e) {
            e.printStackTrace();
            request.resolveError(e);
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<Long, Long> newIdMap = new HashMap<>();
        for (Long oldeStageId : oldStageIdStageIdMap.keySet()) {
            Long newFsId = stageIdFsIdMap.get(oldStageIdStageIdMap.get(oldeStageId));
            if (newFsId != null) {
                newIdMap.put(oldeStageId, newFsId);
            }
        }
        CommitAnswer answer = new CommitAnswer().setStageIdFsIdMap(newIdMap).setNewVersion(fileSyncDatabaseManager.getLatestVersion());
        request.resolveOk(answer);
        // TODO setup transfers
        List<GenericFSEntry> delta = fsDao.getDelta(oldVersion);
        for (GenericFSEntry genericFSEntry : delta) {
            if (!genericFSEntry.getIsDirectory().v() && !genericFSEntry.isSymlink()) {
                DbTransferDetails details = new DbTransferDetails();
                details.getCertId().v(request.getPartnerCertificate().getId().v());
                details.getHash().v(genericFSEntry.getContentHash().v());
                details.getSize().v(genericFSEntry.getSize().v());
                details.getServiceUuid().v(commit.getServiceUuid());
                details.getAvailable().v(true);// client must have that file
                //insert fails if there already is a transfer with that hash
                N.r(() -> transferManager.createTransfer(details));
            }
        }
    }

    public void handleCommit(Request request) throws SqlQueriesException {
        Commit commit = (Commit) request.getPayload();
        BunchOfLocks warden = P.confine(fsDao);
        try {
            if (!canCommit(request, commit))
                return;
            executeCommit(request, commit, warden);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        } finally {
            warden.end();
        }
        transferManager.research();
        Lok.debug("MelDriveServerService.handleCommit");
    }

    @Override
    public boolean onFileTransferred(IFile file, String hash, BunchOfLocks bunchOfLocks) throws SqlQueriesException, IOException {
        boolean isNew = super.onFileTransferred(file, hash, bunchOfLocks);
        if (isNew)
            N.r(() -> hashAvailTimer.start());
        return isNew;
    }

    public void handleAvailableHashesRequest(Request request) throws InvocationTargetException, IllegalAccessException {
        //todo stopped here
        AvailableHashesContainer availableHashesContainer = (AvailableHashesContainer) request.getPayload();
        Lok.error("NOT:IMPLEMENTED:YET.");
        try {
            ISQLResource<FsFile> availableHashes = fileSyncDatabaseManager.getTransferDao().getAvailableTransfersFromHashes(availableHashesContainer.iterator());
            AvailableHashesContainer result = new AvailableHashesContainer(melFileSyncService.getCacheDirectory(), availableHashesContainer.getCacheId(), FileSyncSettings.CACHE_LIST_SIZE);
//            N.sqlResource(availableHashes,sqlResource -> {
//                FsFile fsFile = sqlResource.getNext();
//                while (fsFile!= null){
//                    result.add(new AvailHashEntry(fsFile.getContentHash().v()));
//                    fsFile = sqlResource.getNext();
//                }
//            });
            N.readSqlResource(availableHashes, (sqlResource, fsFile) -> result.add(new AvailHashEntry(fsFile.getContentHash().v())));
            availableHashesContainer.cleanUp();
            result.toDisk();
            result.loadFirstCached();
            request.resolveOk(result);
        } catch (SqlQueriesException | IOException | MelJsonException e) {
            e.printStackTrace();
            request.resolveError(e);
        } finally {
            availableHashesContainer.cleanUp();
        }
    }
}

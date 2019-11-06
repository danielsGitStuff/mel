package de.mel.filesync.service.sync;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
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
                clientData -> melAuthService.connect(clientData.getCertId()).done(
                        mvp -> N.r(() -> mvp.message(clientData.getServiceUuid(), hashesAvailable))));
    });

    public ServerSyncHandler(MelAuthService melAuthService, MelFileSyncService melFileSyncService) {
        super(melAuthService, melFileSyncService);
    }

    @Override
    protected void setupTransferAvailable(DbTransferDetails details, StageSet stageSet, Stage stage) {
        if (stageSet.getSource().equalsValue(FileSyncStrings.STAGESET_SOURCE_CLIENT))
            details.getAvailable().v(true);
    }

    private boolean canCommit(Request request, Commit commit) throws SqlQueriesException {
        final long olderVersion = fileSyncDatabaseManager.getLatestVersion();
        if (commit.getBasedOnVersion() != olderVersion) {
            request.reject(new TooOldVersionException("old version: " + commit.getBasedOnVersion() + " vs " + fileSyncDatabaseManager.getLatestVersion(), fileSyncDatabaseManager.getLatestVersion()));
            return false;
        }
        return true;
    }

    protected void executeCommit(Request request, Commit commit, Warden warden) throws SqlQueriesException {
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
            Long oldId = stage.getId();
            stageDao.insert(stage);
            oldStageIdStageIdMap.put(oldId, stage.getId());
        }
        commit.cleanUp();
        //TODO check goes here

        //map old stage ids with new fs ids
        //fsDao.lockWrite();
        Long oldVersion = fsDao.getLatestVersion();
        Map<Long, Long> stageIdFsIdMap = new HashMap<>();
        try {
            this.commitStage(stageSet.getId().v(), warden, stageIdFsIdMap);
        } catch (OutOfSpaceException e) {
            e.printStackTrace();
            request.reject(e);
            return;
        }
        Map<Long, Long> newIdMap = new HashMap<>();
        for (Long oldeStageId : oldStageIdStageIdMap.keySet()) {
            Long newFsId = stageIdFsIdMap.get(oldStageIdStageIdMap.get(oldeStageId));
            if (newFsId != null) {
                newIdMap.put(oldeStageId, newFsId);
            }
        }
        CommitAnswer answer = new CommitAnswer().setStageIdFsIdMap(newIdMap);
        request.resolve(answer);
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
        Warden warden = P.confine(fsDao);
        try {
            if (!canCommit(request, commit))
                return;
            executeCommit(request, commit, warden);
        } finally {
            warden.end();
        }
        transferManager.research();
        Lok.debug("MelDriveServerService.handleCommit");
    }

    @Override
    public boolean onFileTransferred(AbstractFile file, String hash, Warden warden) throws SqlQueriesException, IOException {
        boolean isNew = super.onFileTransferred(file, hash, warden);
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
            request.resolve(result);
        } catch (SqlQueriesException | IOException | MelJsonException e) {
            e.printStackTrace();
            request.reject(e);
        } finally {
            availableHashesContainer.cleanUp();
        }
    }
}

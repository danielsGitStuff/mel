package de.mel.drive.service.sync;

import de.mel.Lok;
import de.mel.auth.file.AFile;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.core.serialize.exceptions.MelJsonException;
import de.mel.drive.data.*;
import de.mel.drive.quota.OutOfSpaceException;
import de.mel.drive.service.MelDriveService;
import de.mel.drive.sql.*;
import de.mel.drive.tasks.AvailHashEntry;
import de.mel.drive.tasks.AvailableHashesContainer;
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
        hashesAvailable.setIntent(DriveStrings.INTENT_HASH_AVAILABLE);
        N.forEachIgnorantly(driveSettings.getServerSettings().getClients(),
                clientData -> melAuthService.connect(clientData.getCertId()).done(
                        mvp -> N.r(() -> mvp.message(clientData.getServiceUuid(), hashesAvailable))));
    });

    public ServerSyncHandler(MelAuthService melAuthService, MelDriveService melDriveService) {
        super(melAuthService, melDriveService);
    }

    @Override
    protected void setupTransferAvailable(DbTransferDetails details, StageSet stageSet, Stage stage) {
        if (stageSet.getSource().equalsValue(DriveStrings.STAGESET_SOURCE_CLIENT))
            details.getAvailable().v(true);
    }

    private boolean canCommit(Request request, Commit commit) throws SqlQueriesException {
        final long olderVersion = driveDatabaseManager.getLatestVersion();
        if (commit.getBasedOnVersion() != olderVersion) {
            request.reject(new TooOldVersionException("old version: " + commit.getBasedOnVersion() + " vs " + driveDatabaseManager.getLatestVersion(), driveDatabaseManager.getLatestVersion()));
            return false;
        }
        return true;
    }

    protected void executeCommit(Request request, Commit commit, Warden warden) throws SqlQueriesException {
        // stage everything
        StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_CLIENT, request.getPartnerCertificate().getId().v(), commit.getServiceUuid(), null, commit.getBasedOnVersion());
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
    public boolean onFileTransferred(AFile file, String hash, Warden warden) throws SqlQueriesException, IOException {
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
            ISQLResource<FsFile> availableHashes = driveDatabaseManager.getTransferDao().getAvailableTransfersFromHashes(availableHashesContainer.iterator());
            AvailableHashesContainer result = new AvailableHashesContainer(melDriveService.getCacheDirectory(), availableHashesContainer.getCacheId(), DriveSettings.CACHE_LIST_SIZE);
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
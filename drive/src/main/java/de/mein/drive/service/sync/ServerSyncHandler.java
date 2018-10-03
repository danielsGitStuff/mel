package de.mein.drive.service.sync;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.data.Commit;
import de.mein.drive.data.CommitAnswer;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.AvailableHashes;
import de.mein.drive.quota.OutOfSpaceException;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.TransferDetails;
import de.mein.sql.SqlQueriesException;

import java.io.IOException;
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
        N.forEachIgnorantly(driveSettings.getServerSettings().getClients(),
                clientData -> meinAuthService.connect(clientData.getCertId()).done(
                        mvp -> N.r(() -> mvp.message(clientData.getServiceUuid(), DriveStrings.INTENT_HASH_AVAILABLE, hashesAvailable))));
    });

    public ServerSyncHandler(MeinAuthService meinAuthService, MeinDriveService meinDriveService) {
        super(meinAuthService, meinDriveService);
    }

    public void handleCommit(Request request) throws SqlQueriesException {
        // todo threading issues? check for unlocking DAOs after the connection/socket died.
        Commit commit = (Commit) request.getPayload();
        fsDao.lockWrite();
        try {
            final long olderVersion = driveDatabaseManager.getLatestVersion();
            if (commit.getBasedOnVersion() != olderVersion) {
                request.reject(new TooOldVersionException("old version: " + commit.getBasedOnVersion() + " vs " + driveDatabaseManager.getLatestVersion(), driveDatabaseManager.getLatestVersion()));
                return;
            }
            // stage everything
            StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_CLIENT, request.getPartnerCertificate().getId().v(), commit.getServiceUuid(), null);
            Map<Long, Long> oldStageIdStageIdMap = new HashMap<>();
            Iterator<Stage> iterator = commit.iterator();
            while (iterator.hasNext()) {
                Stage stage = iterator.next();
                stage.setStageSet(stageSet.getId().v());
                if (!stage.getIsDirectory()) {
                    if (stage.getDeleted())
                        stage.setSynced(true);
                    else
                        stage.setSynced(false);
                }
                // set "new" parent id
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
                this.commitStage(stageSet.getId().v(), false, stageIdFsIdMap);
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
                if (!genericFSEntry.getIsDirectory().v()) {
                    TransferDetails details = new TransferDetails();
                    details.getCertId().v(request.getPartnerCertificate().getId().v());
                    details.getHash().v(genericFSEntry.getContentHash().v());
                    details.getSize().v(genericFSEntry.getSize().v());
                    details.getServiceUuid().v(commit.getServiceUuid());
                    details.getAvailable().v(true);// client must have that file
                    //insert fails if there already is a transfer with that hash
                    N.r(() -> transferManager.createTransfer(details));
                }
            }
        } finally {
            fsDao.unlockWrite();
        }
        transferManager.research();
        Lok.debug("MeinDriveServerService.handleCommit");
    }

    @Override
    public boolean onFileTransferred(AFile file, String hash) throws SqlQueriesException, IOException {
        boolean isNew = super.onFileTransferred(file, hash);
        if (isNew)
            N.r(() -> hashAvailTimer.start());
        return isNew;
    }
}

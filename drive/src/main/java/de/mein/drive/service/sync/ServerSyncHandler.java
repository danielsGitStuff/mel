package de.mein.drive.service.sync;

import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.drive.data.Commit;
import de.mein.drive.data.CommitAnswer;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.TransferDetails;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.SqlQueriesException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 1/26/17.
 */
public class ServerSyncHandler extends SyncHandler {
    public ServerSyncHandler(MeinAuthService meinAuthService, MeinDriveService meinDriveService) {
        super(meinAuthService, meinDriveService);
    }

    public void handleCommit(Request request) throws SqlQueriesException {
        // todo threading issues? check for unlocking DAOs after the connection/socket died.
        Commit commit = (Commit) request.getPayload();
        //todo debug hier weiter
        System.out.println("ServerSyncHandler.handleCommit.debugXXXXXXXXX");
        if (commit.getBasedOnVersion() != driveDatabaseManager.getLatestVersion()) {
            request.reject(new TooOldVersionException("server :" + driveDatabaseManager.getLatestVersion() + " vs " + commit.getBasedOnVersion()));
            return;
        }
        StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_CLIENT, request.getPartnerCertificate().getId().v(), commit.getServiceUuid());
        Map<Long, Long> oldStageIdStageIdMap = new HashMap<>();
        for (Stage stage : commit.getStages()) {
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
        //TODO check goes here

        //map old stage ids with new fs ids
        fsDao.lockWrite();
        Long oldVersion = fsDao.getLatestVersion();
        Map<Long, Long> stageIdFsIdMap = new HashMap<>();
        this.commitStage(stageSet.getId().v(), false, stageIdFsIdMap);
        Map<Long, Long> newIdMap = new HashMap<>();
        for (Long oldeStageId : oldStageIdStageIdMap.keySet()) {
            Long newFsId = stageIdFsIdMap.get(oldStageIdStageIdMap.get(oldeStageId));
            if (newFsId != null) {
                newIdMap.put(oldeStageId, newFsId);
            }
        }
        CommitAnswer answer = new CommitAnswer().setStageIdFsIdMap(newIdMap);
        request.resolve(answer);
        fsDao.unlockWrite();
        // TODO setup transfers
        List<GenericFSEntry> delta = fsDao.getDelta(oldVersion);
        for (GenericFSEntry genericFSEntry : delta) {
            if (!genericFSEntry.getIsDirectory().v()) {
                TransferDetails details = new TransferDetails();
                details.getCertId().v(request.getPartnerCertificate().getId().v());
                details.getHash().v(genericFSEntry.getContentHash().v());
                details.getSize().v(genericFSEntry.getSize().v());
                details.getServiceUuid().v(commit.getServiceUuid());
                transferManager.createTransfer(details);
            }
        }
        transferManager.research();
        System.out.println("MeinDriveServerService.handleCommit");
    }
}

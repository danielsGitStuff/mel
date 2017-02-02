package de.mein.drive.service;

import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.drive.data.Commit;
import de.mein.drive.data.CommitAnswer;
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
        Commit commit = (Commit) request.getPayload();
        StageDao stageDao = driveDatabaseManager.getStageDao();
        FsDao fsDao = driveDatabaseManager.getFsDao();
        StageSet stageSet = stageDao.createStageSet("commit", request.getPartnerCertificate().getId().v(), commit.getServiceUuid());
        Map<Long, Long> oldStageIdStageIdMap = new HashMap<>();
        for (Stage stage : commit.getStages()) {
            stage.setStageSet(stageSet.getId().v());
            if (!stage.getIsDirectory())
                stage.setSynced(false);
            Long oldId = stage.getId();
            stageDao.insert(stage);
            oldStageIdStageIdMap.put(oldId, stage.getId());
        }
        //TODO check goes here

        //map old stage ids with new fs ids
        fsDao.lockWrite();
        Long oldVersion = fsDao.getLatestVersion();
        Map<Long, Long> stageIdFsIdMap = new HashMap<>();
        this.commitStage(stageSet.getId().v(),false,stageIdFsIdMap);
        List<GenericFSEntry> delta = fsDao.getDelta(oldVersion);
        CommitAnswer answer = new CommitAnswer().setDelta(delta).setStageIdFsIdMao(stageIdFsIdMap);
        request.resolve(answer);
        fsDao.unlockWrite();
        // TODO setup transfers
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

package de.mein.drive.service;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceMessageHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.NoTryRunner;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.Commit;
import de.mein.drive.data.CommitAnswer;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.jobs.FsSyncJob;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.tasks.SyncTask;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Created by xor on 10/21/16.
 */
public class MeinDriveClientService extends MeinDriveService<ClientSyncHandler> {
    private static Logger logger = Logger.getLogger(MeinDriveClientService.class.getName());
    private DriveSyncListener syncListener;

    public MeinDriveClientService(MeinAuthService meinAuthService) {
        super(meinAuthService);
    }

    @Override
    protected void handleFsSyncJob(FsSyncJob fsSyncJob) throws IOException, SqlQueriesException {
        this.doFsSyncJob(fsSyncJob).done(stageSetId -> runner.runTry(() -> {
            System.out.println(meinAuthService.getName() + ".MeinDriveService.workWork.STAGE.DONE");
            meinAuthService.connect(driveSettings.getClientSettings().getServerCertId()).done(mvp -> NoTryRunner.run(() -> {
                Commit stageSet = new Commit().setStages(driveDatabaseManager.getStageDao().getStagesByStageSet(stageSetId)).setServiceUuid(getUuid());
                mvp.request(driveSettings.getClientSettings().getServerServiceUuid(), DriveStrings.INTENT_COMMIT, stageSet).done(result -> NoTryRunner.run(() -> {
                    CommitAnswer answer = (CommitAnswer) result;
                    FsDao fsDao = driveDatabaseManager.getFsDao();
                    StageDao stageDao = driveDatabaseManager.getStageDao();
                    fsDao.lockWrite();
                    for (Stage stage : stageDao.getStagesByStageSet(stageSetId)) {
                        Long fsId = answer.getStageIdFsIdMao().get(stage.getId());
                        if (fsId != null) {
                            stage.setFsId(fsId);
                            if (stage.getParentId() != null) {
                                Long fsParentId = answer.getStageIdFsIdMao().get(stage.getParentId());
                                if (fsParentId != null)
                                    stage.setFsParentId(fsParentId);
                            }
                            stageDao.update(stage);
                        }
                    }
                    //TODO conflict detection goes here
                    syncHandler.commitStage(stageSetId,false);
                    fsDao.unlockWrite();
                }));
            }));
        }));
    }


    @Override
    protected void onSyncReceived(Request request) {
        try {
            syncHandler.syncThisClient();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected boolean workWorkWork(Job unknownJob) {
        System.out.println(meinAuthService.getName() + ".MeinDriveClientService.workWorkWork :)");
        if (unknownJob instanceof ServiceMessageHandlerJob) {
            ServiceMessageHandlerJob job = (ServiceMessageHandlerJob) unknownJob;
            if (job.isRequest()) {
                Request request = job.getRequest();

            } else if (job.isMessage()) {
                if (job.getIntent().equals(DriveStrings.INTENT_PROPAGATE_NEW_VERSION)) {
                    DriveDetails driveDetails = (DriveDetails) job.getMessage();
                    driveDetails.getLastSyncVersion();
                    try {
                        syncHandler.syncThisClient();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return false;
    }


    public void setSyncListener(DriveSyncListener syncListener) {
        this.syncListener = syncListener;
    }


    @Override
    protected ClientSyncHandler initSyncHandler() {
        return new ClientSyncHandler(meinAuthService, this);
    }

    public void syncThisClient() throws InterruptedException, SqlQueriesException {
        syncHandler.syncThisClient();
    }
}

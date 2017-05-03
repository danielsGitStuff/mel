package de.mein.drive.service;

import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceMessageHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.Commit;
import de.mein.drive.data.CommitAnswer;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;

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
        } else if (unknownJob instanceof Job.CertificateSpottedJob) {
            Job.CertificateSpottedJob spottedJob = (Job.CertificateSpottedJob) unknownJob;
            //check if connected certificate is the server. if so: sync()
            if (driveSettings.getClientSettings().getServerCertId().equals(spottedJob.getPartnerCertificate().getId().v())) {
                try {
                    syncThisClient();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (unknownJob instanceof CommitJob) {
            CommitJob commitJob = (CommitJob) unknownJob;
            syncHandler.commitJob();
        }
        return false;
    }


    public void setSyncListener(DriveSyncListener syncListener) {
        this.syncListener = syncListener;
    }

    public DriveSyncListener getSyncListener() {
        return syncListener;
    }

    @Override
    protected ClientSyncHandler initSyncHandler() {
        return new ClientSyncHandler(meinAuthService, this);
    }

    public void syncThisClient() throws InterruptedException, SqlQueriesException {
        syncHandler.syncThisClient();
    }

    @Override
    public void initDatabase(DriveDatabaseManager driveDatabaseManager) throws SqlQueriesException {
        super.initDatabase(driveDatabaseManager);
        this.stageIndexer.setStagingDoneListener(stageSetId -> {
            // stage is complete. first lock on FS
            FsDao fsDao = driveDatabaseManager.getFsDao();
            StageDao stageDao = driveDatabaseManager.getStageDao();
            fsDao.unlockRead();
            //fsDao.lockWrite();
            stageDao.lockRead();

            if (stageDao.stageSetHasContent(stageSetId)) {

                //all other stages we can find at this point are complete/valid and wait at this point.
                //todo conflict checking goes here - has to block

                Promise<MeinValidationProcess, Exception, Void> connectedPromise = meinAuthService.connect(driveSettings.getClientSettings().getServerCertId());
                connectedPromise.done(mvp -> N.r(() -> {
                    Commit commit = new Commit().setStages(driveDatabaseManager.getStageDao().getStagesByStageSetList(stageSetId)).setServiceUuid(getUuid());
                    mvp.request(driveSettings.getClientSettings().getServerServiceUuid(), DriveStrings.INTENT_COMMIT, commit).done(result -> N.r(() -> {
                        //fsDao.lockWrite();
                        CommitAnswer answer = (CommitAnswer) result;
                        ISQLResource<Stage> stages = stageDao.getStagesByStageSet(stageSetId);
                        Stage stage = stages.getNext();
                        while (stage != null) {
                            Long fsId = answer.getStageIdFsIdMap().get(stage.getId());
                            if (fsId != null) {
                                stage.setFsId(fsId);
                                if (stage.getParentId() != null) {
                                    Long fsParentId = answer.getStageIdFsIdMap().get(stage.getParentId());
                                    if (fsParentId != null)
                                        stage.setFsParentId(fsParentId);
                                }
                                stageDao.update(stage);
                            }
                            stage = stages.getNext();
                        }
                        StageSet stageSet = stageDao.getStageSetById(stageSetId);
                        stageSet.setStatus(DriveStrings.STAGESET_STATUS_SERVER_COMMITED);
                        stageDao.updateStageSet(stageSet);
                        addJob(new CommitJob());
                        //syncHandler.commitStage(stageSetId, false);
                        //fsDao.unlockWrite();
                    }));

                }));
                connectedPromise.fail(result -> {
                    // todo server did not commit. it probably had a local change. have to solve it here
                    fsDao.unlockWrite();
                    stageDao.unlockRead();
                });
            } else {
                stageDao.deleteStageSet(stageSetId);
//                fsDao.unlockWrite();
                stageDao.unlockRead();
            }
        });
    }

}

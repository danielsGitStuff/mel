package de.mein.drive.service;

import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceMessageHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveServerSettingsDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.sync.ServerSyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.tasks.SyncTask;
import de.mein.sql.SqlQueriesException;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 10/21/16.
 */
public class MeinDriveServerService extends MeinDriveService<ServerSyncHandler> {
    private static Logger logger = Logger.getLogger(MeinDriveServerService.class.getName());

    public MeinDriveServerService(MeinAuthService meinAuthService) {
        super(meinAuthService);
    }


    @Override
    protected void onSyncReceived(Request request) {
        logger.log(Level.FINEST, "MeinDriveServerService.onSyncReceived");
        SyncTask task = (SyncTask) request.getPayload();
        driveDatabaseManager.getFsDao().lockRead();
        try {
            List<GenericFSEntry> delta = driveDatabaseManager.getDelta(task.getVersion());
            request.resolve(task.setResult(delta));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driveDatabaseManager.getFsDao().unlockRead();
        }
    }


    @Override
    protected boolean workWorkWork(Job unknownJob) {
        logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveServerService.workWorkWork :)");
        try {
            if (unknownJob instanceof ServiceMessageHandlerJob) {
                ServiceMessageHandlerJob job = (ServiceMessageHandlerJob) unknownJob;
                if (job.isRequest()) {
                    Request request = job.getRequest();
                    if (checkIntent(request, DriveStrings.INTENT_REG_AS_CLIENT)) {
                        DriveDetails driveDetails = (DriveDetails) request.getPayload();
                        Long certId = request.getPartnerCertificate().getId().v();
                        driveSettings.getServerSettings().addClient(certId, driveDetails.getServiceUuid());
                        driveSettings.save();
                        request.resolve(null);
                        return true;
                    } else if (checkIntent(request, DriveStrings.INTENT_COMMIT)) {
                        syncHandler.handleCommit(request);
                        return true;
                    }
                } else if (job.isMessage()) {

                }
            }
//            else if (unknownJob instanceof FsSyncJob) {
//                System.out.println("MeinDriveServerService.workWorkWork.SYNC");
//                FsSyncJob syncJob = (FsSyncJob) unknownJob;
//                Promise<Long, Exception, Void> indexedPromise = doFsSyncJob(syncJob);
//                indexedPromise.done(stageSetId -> {
//                    syncHandler.commitStage(stageSetId);
//                });
//                return true;
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected ServerSyncHandler initSyncHandler() {
        return new ServerSyncHandler(meinAuthService, this);
    }


    private void propagateNewVersion() {
        try {
            long version = driveDatabaseManager.getLatestVersion();
            for (DriveServerSettingsDetails.ClientData client : driveDatabaseManager.getDriveSettings().getServerSettings().getClients()) {
                meinAuthService.connect(client.getCertId()).done(mvp -> N.r(() -> {
                    mvp.message(client.getServiceUuid(), DriveStrings.INTENT_PROPAGATE_NEW_VERSION, new DriveDetails().setLastSyncVersion(version));
                }));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void initDatabase(DriveDatabaseManager driveDatabaseManager) throws SqlQueriesException {
        super.initDatabase(driveDatabaseManager);
        stageIndexer.setStagingDoneListener(stageSetId -> {
            logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.workWork.STAGE.DONE");
            // staging is done. stage data is up to date. time to commit to fs
            FsDao fsDao = driveDatabaseManager.getFsDao();
            StageDao stageDao = driveDatabaseManager.getStageDao();
            fsDao.unlockRead();
            if (stageDao.stageSetHasContent(stageSetId)) {
                fsDao.lockWrite();
                //todo conflict checks
                syncHandler.commitStage(stageSetId, false);
                fsDao.unlockWrite();
                propagateNewVersion();
            } else {
                stageDao.deleteStageSet(stageSetId);
            }
            /*
            // tell everyone fs has a new version
            for (DriveServerSettingsDetails.ClientData clientData : driveSettings.getServerSettings().getClients()) {
                Certificate client = meinAuthService.getCertificateManager().getTrustedCertificateById(clientData.getCertId());
                Promise<MeinValidationProcess, Exception, Void> promise = meinAuthService.connect(client.getId().v(), client.getAddress().v(), client.getPort().v(), client.getCertDeliveryPort().v(), false);
                promise.done(meinValidationProcess -> runner.runTry(() -> {
                    logger.log(Level.FINEST, "MeinDriveService.workWork.syncThisClient.msg");
                    meinValidationProcess.message(clientData.getServiceUuid(), DriveStrings.INTENT_SYNC, null);
                }));
            }*/
        });
    }

    @Override
    public void onShutDown() {
        super.onShutDown();
    }
}

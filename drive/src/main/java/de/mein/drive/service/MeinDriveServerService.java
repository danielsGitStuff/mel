package de.mein.drive.service;

import de.mein.DeferredRunnable;
import de.mein.auth.data.ClientData;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceRequestHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.IndexListener;
import de.mein.drive.service.sync.ServerSyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.tasks.SyncTask;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 10/21/16.
 */
public class MeinDriveServerService extends MeinDriveService<ServerSyncHandler> {
    private static Logger logger = Logger.getLogger(MeinDriveServerService.class.getName());

    public MeinDriveServerService(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String uuid) {
        super(meinAuthService, workingDirectory, serviceTypeId, uuid);
    }


    @Override
    protected void onSyncReceived(Request request) {
        logger.log(Level.FINEST, "MeinDriveServerService.onSyncReceived");
        SyncTask task = (SyncTask) request.getPayload();
        driveDatabaseManager.getFsDao().lockRead();
        try {
            ISQLResource<GenericFSEntry> delta = driveDatabaseManager.getDeltaResource(task.getOldVersion());
            task.setCacheDirectory(cacheDirectory);
            GenericFSEntry next = delta.getNext();
            while (next != null) {
                task.add(next);
                next = delta.getNext();
            }
            task.toDisk();
            task.loadFirstCached();
            task.setNewVersion(driveDatabaseManager.getLatestVersion());
            request.resolve(task);
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
            if (unknownJob instanceof ServiceRequestHandlerJob) {
                ServiceRequestHandlerJob job = (ServiceRequestHandlerJob) unknownJob;
                if (job.isRequest()) {
                    Request request = job.getRequest();
                    if (checkIntent(request, DriveStrings.INTENT_REG_AS_CLIENT)) {
                        DriveDetails driveDetails = (DriveDetails) request.getPayload();
                        Long certId = request.getPartnerCertificate().getId().v();
                        driveSettings.getServerSettings().addClient(certId, driveDetails.getServiceUuid());
                        driveSettings.save();
                        //propagateNewVersion();
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
            for (ClientData client : driveDatabaseManager.getDriveSettings().getServerSettings().getClients()) {
                meinAuthService.connect(client.getCertId()).done(mvp -> N.r(() -> {
                    mvp.message(client.getServiceUuid(), DriveStrings.INTENT_PROPAGATE_NEW_VERSION, new DriveDetails().setLastSyncVersion(version));
                }));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public DeferredObject<DeferredRunnable, Exception, Void> startIndexer(DriveDatabaseManager driveDatabaseManager) throws SqlQueriesException {
        DeferredObject<DeferredRunnable, Exception, Void> indexingDone = super.startIndexer(driveDatabaseManager);
        stageIndexer.setStagingDoneListener(stageSetId -> {
            logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.workWork.STAGE.DONE");
            // staging is done. stage data is up to date. time to commit to fs
            FsDao fsDao = driveDatabaseManager.getFsDao();
            StageDao stageDao = driveDatabaseManager.getStageDao();
            //fsDao.unlockRead();
            if (stageDao.stageSetHasContent(stageSetId)) {
                fsDao.lockWrite();
                //todo conflict checks
                N.r(() -> syncHandler.commitStage(stageSetId, false));
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
                    logger.log(Level.FINEST, "MeinDriveService.workWork.syncFromServer.msg");
                    meinValidationProcess.message(clientData.getServiceUuid(), DriveStrings.INTENT_SYNC, null);
                }));
            }*/
        });
        return indexingDone;
    }

    @Override
    protected IndexListener createIndexListener() {
        IndexListener indexListener = new IndexListener() {
            @Override
            public void foundFile(FsFile fsFile) {

            }

            @Override
            public void foundDirectory(FsDirectory fsDirectory) {

            }

            @Override
            public void done(Long stageSetId) {
                N.r(() -> {
                    driveDatabaseManager.updateVersion();
                    if (driveDatabaseManager.getMeinDriveService() instanceof MeinDriveServerService)
                        syncHandler.commitStage(stageSetId);
                });
            }
        };
        return indexListener;
    }

    @Override
    public void onShutDown() {
        super.onShutDown();
    }


    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newCachedThreadPool(threadFactory);
    }

    @Override
    public void onMeinAuthIsUp() {
        startIndexerDonePromise.done(result -> {
            System.out.println("MeinDriveServerService.onMeinAuthIsUp");
            // connect to every client that we know
            for (ClientData client : this.driveSettings.getServerSettings().getClients()) {
                N.r(() -> meinAuthService.connect(client.getCertId()));
            }
        });
    }
}

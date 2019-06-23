package de.mein.drive.service;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.data.ClientData;
import de.mein.auth.data.cached.CachedData;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceRequestHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import de.mein.core.serialize.SerializableEntity;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.IndexListener;
import de.mein.drive.service.sync.ServerSyncHandler;
import de.mein.drive.sql.FsDirectory;
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


/**
 * Created by xor on 10/21/16.
 */
public class MeinDriveServerService extends MeinDriveService<ServerSyncHandler> {

    public MeinDriveServerService(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String uuid, DriveSettings driveSettings) {
        super(meinAuthService, workingDirectory, serviceTypeId, uuid, driveSettings);
    }

    @Override
    public void onIndexerDone() {
//        N.forEachAdvIgnorantly(driveSettings.getServerSettings().getClients(), (stoppable, index, clientData) -> {
//            meinAuthService.connect(clientData.getCertId());
//        });
    }


    @Override
    protected void onSyncReceived(Request request) {
        Lok.debug("MeinDriveServerService.onSyncReceived");
        SyncTask task = (SyncTask) request.getPayload();
        SyncTask answer = new SyncTask(cacheDirectory, DriveSettings.CACHE_LIST_SIZE);
        answer.setCacheId(CachedData.randomId());
        Transaction transaction = T.lockingTransaction(T.read(driveDatabaseManager.getFsDao()));
        try {
            ISQLResource<GenericFSEntry> delta = driveDatabaseManager.getDeltaResource(task.getOldVersion());
            GenericFSEntry next = delta.getNext();
            while (next != null) {
                answer.add(next);
                next = delta.getNext();
            }
            answer.toDisk();
            answer.loadFirstCached();
            answer.setNewVersion(driveDatabaseManager.getLatestVersion());
            request.resolve(answer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            transaction.end();
        }
    }


    @Override
    protected boolean workWorkWork(Job unknownJob) {
        Lok.debug(meinAuthService.getName() + ".MeinDriveServerService.workWorkWork :)");
        try {
            if (unknownJob instanceof ServiceRequestHandlerJob) {
                ServiceRequestHandlerJob job = (ServiceRequestHandlerJob) unknownJob;
                if (job.isRequest()) {
                    Request request = job.getRequest();
                    if (request.hasIntent(DriveStrings.INTENT_REG_AS_CLIENT)) {
                        DriveDetails driveDetails = (DriveDetails) request.getPayload();
                        Long certId = request.getPartnerCertificate().getId().v();
                        driveSettings.getServerSettings().addClient(certId, driveDetails.getServiceUuid());
                        driveSettings.save();
                        //propagateNewVersion();
                        request.resolve(null);
                        return true;
                    } else if (request.hasIntent(DriveStrings.INTENT_COMMIT)) {
                        syncHandler.handleCommit(request);
                        return true;
                    } else if (request.hasIntent(DriveStrings.INTENT_ASK_HASHES_AVAILABLE)) {
                        syncHandler.handleAvailableHashesRequest(request);
                        return true;
                    }
                } else if (job.isMessage()) {
                    System.out.println("MeinDriveServerService.workWorkWork");
                }
            } else if (unknownJob instanceof Job.ConnectionAuthenticatedJob) {
                Job.ConnectionAuthenticatedJob authenticatedJob = (Job.ConnectionAuthenticatedJob) unknownJob;
                if (driveSettings.getServerSettings().hasClient(authenticatedJob.getPartnerCertificate().getId().v())) {
                    this.syncHandler.resume();
                }
            }
//            else if (unknownJob instanceof FsSyncJob) {
//                Lok.debug("MeinDriveServerService.workWorkWork.SYNC");
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
                    mvp.message(client.getServiceUuid(), new DriveDetails().setLastSyncVersion(version).setIntent(DriveStrings.INTENT_PROPAGATE_NEW_VERSION));
                }));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public DeferredObject<DeferredRunnable, Exception, Void> startIndexer() throws SqlQueriesException {
        DeferredObject<DeferredRunnable, Exception, Void> indexingDone = super.startIndexer();
        stageIndexer.setStagingDoneListener(stageSetId -> {
            Lok.debug(meinAuthService.getName() + ".MeinDriveService.workWork.STAGE.DONE");
            // staging is done. stage data is up to date. time to commit to fs
            FsDao fsDao = driveDatabaseManager.getFsDao();
            StageDao stageDao = driveDatabaseManager.getStageDao();
            //fsDao.unlockRead();
            if (stageDao.stageSetHasContent(stageSetId)) {
                Transaction transaction = T.lockingTransaction(fsDao);
                //todo conflict checks
                N.r(() -> syncHandler.commitStage(stageSetId, transaction));
                transaction.end();
                Lok.debug("committed stageset " + stageSetId);
                propagateNewVersion();
            } else {
                stageDao.deleteStageSet(stageSetId);
                Lok.debug("deleted stageset " + stageSetId);

            }
            /*
            // tell everyone fs has a new version
            for (DriveServerSettingsDetails.ClientData clientData : driveSettings.getServerSettings().getClients()) {
                Certificate client = meinAuthService.getCertificateManager().getTrustedCertificateById(clientData.getCertId());
                Promise<MeinValidationProcess, Exception, Void> promise = meinAuthService.connect(client.getId().v(), client.getAddress().v(), client.getPort().v(), client.getCertDeliveryPort().v(), false);
                promise.done(meinValidationProcess -> runner.runTry(() -> {
                   Lok.debug("MeinDriveService.workWork.syncFromServer.msg");
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
            public void foundDirectory(FsDirectory fsDirectory) {

            }

            @Override
            public void done(Long stageSetId, Transaction transaction) {
                N.r(() -> {
                    driveDatabaseManager.updateVersion();
                    if (stageSetId != null) {
                        if (driveDatabaseManager.getMeinDriveService() instanceof MeinDriveServerService)
                            syncHandler.commitStage(stageSetId, transaction);
                    } else {
                        Lok.debug("MeinDriveServerService.done(). StageSet was empty");
                    }
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
    public void onBootLevel2Finished() {
//        startIndexerDonePromise.done(result -> {
        Lok.debug("MeinDriveServerService.onServiceRegistered");
        // connect to every client that we know
        for (ClientData client : this.driveSettings.getServerSettings().getClients()) {
            N.r(() -> meinAuthService.connect(client.getCertId()));
        }
//        });
    }


    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newCachedThreadPool(threadFactory);
    }

    @Override
    public void onServiceRegistered() {
        Lok.debug("registered");
    }

    @Override
    public SerializableEntity addAdditionalServiceInfo() {
        return driveSettings.getDriveDetails();
    }
}

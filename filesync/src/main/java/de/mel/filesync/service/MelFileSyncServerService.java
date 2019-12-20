package de.mel.filesync.service;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.data.ClientData;
import de.mel.auth.data.cached.CachedInitializer;
import de.mel.auth.jobs.Job;
import de.mel.auth.jobs.ServiceRequestHandlerJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.core.serialize.SerializableEntity;
import de.mel.filesync.data.FileSyncDetails;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.SyncAnswer;
import de.mel.filesync.index.IndexListener;
import de.mel.filesync.service.sync.ServerSyncHandler;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.TransferState;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.filesync.tasks.SyncRequest;
import de.mel.sql.ISQLResource;
import de.mel.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


/**
 * Created by xor on 10/21/16.
 */
public class MelFileSyncServerService extends MelFileSyncService<ServerSyncHandler> {

    public MelFileSyncServerService(MelAuthService melAuthService, File workingDirectory, Long serviceTypeId, String uuid, FileSyncSettings fileSyncSettings, FileSyncDatabaseManager databaseManager) {
        super(melAuthService, workingDirectory, serviceTypeId, uuid, fileSyncSettings, databaseManager);
    }

    @Override
    public void onIndexerDone() {
//        N.forEachAdvIgnorantly(driveSettings.getServerSettings().getClients(), (stoppable, index, clientData) -> {
//            melAuthService.connect(clientData.getCertId());
//        });
    }


    @Override
    protected void onSyncReceived(Request request) {
        Lok.debug("MelDriveServerService.onSyncReceived");
        SyncRequest task = (SyncRequest) request.getPayload();
        SyncAnswer answer = new SyncAnswer(cacheDirectory, CachedInitializer.randomId(), FileSyncSettings.CACHE_LIST_SIZE);
        Warden warden = P.confine(P.read(fileSyncDatabaseManager.getFsDao()));
        try {
            ISQLResource<GenericFSEntry> delta = fileSyncDatabaseManager.getDeltaResource(task.getOldVersion());
            GenericFSEntry next = delta.getNext();
            while (next != null) {
                answer.add(next);
                next = delta.getNext();
            }
            answer.toDisk();
            answer.loadFirstCached();
            answer.setNewVersion(fileSyncDatabaseManager.getLatestVersion());
            request.resolve(answer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            warden.end();
        }
    }


    @Override
    protected boolean workWorkWork(Job unknownJob) {
        Lok.debug(melAuthService.getName() + ".MelDriveServerService.workWorkWork :)");
        try {
            if (unknownJob instanceof ServiceRequestHandlerJob) {
                ServiceRequestHandlerJob job = (ServiceRequestHandlerJob) unknownJob;
                if (job.isRequest()) {
                    Request request = job.getRequest();
                    if (request.hasIntent(FileSyncStrings.INTENT_REG_AS_CLIENT)) {
                        FileSyncDetails fileSyncDetails = (FileSyncDetails) request.getPayload();
                        Long certId = request.getPartnerCertificate().getId().v();
                        fileSyncSettings.getServerSettings().addClient(certId, fileSyncDetails.getServiceUuid());
                        fileSyncSettings.save();
                        //propagateNewVersion();
                        request.resolve(null);
                        return true;
                    } else if (request.hasIntent(FileSyncStrings.INTENT_COMMIT)) {
                        syncHandler.handleCommit(request);
                        return true;
                    } else if (request.hasIntent(FileSyncStrings.INTENT_ASK_HASHES_AVAILABLE)) {
                        syncHandler.handleAvailableHashesRequest(request);
                        return true;
                    }
                } else if (job.isMessage()) {
                    System.out.println("MelDriveServerService.workWorkWork");
                }
            } else if (unknownJob instanceof Job.ConnectionAuthenticatedJob) {
                Job.ConnectionAuthenticatedJob authenticatedJob = (Job.ConnectionAuthenticatedJob) unknownJob;
                P.confine(fileSyncDatabaseManager.getTransferDao())
                        .run(() -> {
                            ClientData clientData = fileSyncSettings.getServerSettings().getClientData(authenticatedJob.getPartnerCertificate().getId().v());
                            if (clientData != null) {
                                fileSyncDatabaseManager.getTransferDao().flagStateForRemainingTransfers(clientData.getCertId(), clientData.getServiceUuid(), TransferState.NOT_STARTED);
                                syncHandler.researchTransfers();
                            }
                        })
                        .end();
            } else if (unknownJob instanceof Job.CertificateSpottedJob) {
                // reset the remaining transfers so we can try again
                Job.CertificateSpottedJob spottedJob = (Job.CertificateSpottedJob) unknownJob;
                P.confine(fileSyncDatabaseManager.getTransferDao())
                        .run(() -> {
                            ClientData clientData = fileSyncSettings.getServerSettings().getClientData(spottedJob.getPartnerCertificate().getId().v());
                            fileSyncDatabaseManager.getTransferDao().flagStateForRemainingTransfers(clientData.getCertId(), clientData.getServiceUuid(), TransferState.NOT_STARTED);
                            syncHandler.researchTransfers();
                        })
                        .end();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected ServerSyncHandler initSyncHandler() {
        return new ServerSyncHandler(melAuthService, this);
    }


    private void propagateNewVersion() {
        try {
            long version = fileSyncDatabaseManager.getLatestVersion();
            for (ClientData client : fileSyncDatabaseManager.getFileSyncSettings().getServerSettings().getClients()) {
                melAuthService.connect(client.getCertId()).done(mvp -> N.r(() -> {
                    mvp.message(client.getServiceUuid(), new FileSyncDetails().setLastSyncVersion(version).setIntent(FileSyncStrings.INTENT_PROPAGATE_NEW_VERSION));
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
            Lok.debug(melAuthService.getName() + " first indexing done!");
            // staging is done. stage data is up to date. time to commit to fs
            FsDao fsDao = fileSyncDatabaseManager.getFsDao();
            StageDao stageDao = fileSyncDatabaseManager.getStageDao();
            //fsDao.unlockRead();
            if (stageDao.stageSetHasContent(stageSetId)) {
                Warden warden = P.confine(fsDao);
                //todo conflict checks
                N.r(() -> syncHandler.commitStage(stageSetId, warden));
                warden.end();
                Lok.debug("committed stageset " + stageSetId);
                propagateNewVersion();
            } else {
                stageDao.deleteStageSet(stageSetId);
                Lok.debug("deleted stageset " + stageSetId);

            }
            /*
            // tell everyone fs has a new version
            for (DriveServerSettingsDetails.ClientData clientData : driveSettings.getServerSettings().getClients()) {
                Certificate client = melAuthService.getCertificateManager().getTrustedCertificateById(clientData.getCertId());
                Promise<MelValidationProcess, Exception, Void> promise = melAuthService.connect(client.getId().v(), client.getAddress().v(), client.getPort().v(), client.getCertDeliveryPort().v(), false);
                promise.done(melValidationProcess -> runner.runTry(() -> {
                   Lok.debug("MelDriveService.workWork.syncFromServer.msg");
                    melValidationProcess.message(clientData.getServiceUuid(), DriveStrings.INTENT_SYNC, null);
                }));
            }*/
        });
        return indexingDone;
    }

    @Override
    protected IndexListener createIndexListener() {
        return (stageSetId, transaction) -> N.r(() -> {
            fileSyncDatabaseManager.updateVersion();
            if (stageSetId != null) {
                syncHandler.commitStage(stageSetId, transaction);
            } else {
                Lok.debug("MelDriveServerService.done(). StageSet was empty");
            }
        });
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        super.onShutDown();
        return null;
    }

    @Override
    public void onBootLevel2Finished() {
        Lok.debug("MelDriveServerService.onServiceRegistered");
        // connect to every client that we know
        for (ClientData client : this.fileSyncSettings.getServerSettings().getClients()) {
            N.r(() -> melAuthService.connect(client.getCertId()));
        }
        N.r(() -> startedPromise.resolve(this));
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
        FileSyncDetails fileSyncDetails = fileSyncSettings.getDriveDetails();
        fileSyncDetails.setDirectoryCount(fileSyncDatabaseManager.getFsDao().countDirectories());
        return fileSyncDetails;
    }


}

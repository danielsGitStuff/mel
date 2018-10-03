package de.mein.drive.service;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceRequestHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.data.AvailableHashes;
import de.mein.drive.data.DriveClientSettingsDetails;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.index.IndexListener;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.jobs.SyncClientJob;
import de.mein.drive.service.sync.ClientSyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.sql.dao.TransferDao;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 * Created by xor on 10/21/16.
 */
public class MeinDriveClientService extends MeinDriveService<ClientSyncHandler> {

    private static Logger logger = Logger.getLogger(MeinDriveClientService.class.getName());

    public MeinDriveClientService(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String uuid) {
        super(meinAuthService, workingDirectory, serviceTypeId, uuid);
    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newCachedThreadPool(threadFactory);
    }


    @Override
    protected void onSyncReceived(Request request) {
        try {
            addJob(new SyncClientJob());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    protected boolean workWorkWork(Job unknownJob) {
        Lok.debug(meinAuthService.getName() + ".MeinDriveClientService.workWorkWork :)");
        if (unknownJob instanceof ServiceRequestHandlerJob) {
            ServiceRequestHandlerJob job = (ServiceRequestHandlerJob) unknownJob;
            if (job.isRequest()) {
                Request request = job.getRequest();

            } else if (job.isMessage()) {
                if (job.getIntent().equals(DriveStrings.INTENT_PROPAGATE_NEW_VERSION)) {
                    DriveDetails driveDetails = (DriveDetails) job.getPayLoad();
                    driveDetails.getLastSyncVersion();
                    try {
                        addJob(new SyncClientJob());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (job.getIntent().equals(DriveStrings.INTENT_HASH_AVAILABLE)) {
                    AvailableHashes availableHashes = (AvailableHashes) job.getPayLoad();
                    updateHashes(availableHashes.getHashes());
                    //addJob(new UpdateHashesJob(availableHashes.getHashes()));
                }
            }
        } else if (unknownJob instanceof Job.CertificateSpottedJob) {
            Job.CertificateSpottedJob spottedJob = (Job.CertificateSpottedJob) unknownJob;
            //check if connected certificate is the server. if so: sync()
            if (driveSettings.getClientSettings().getServerCertId().equals(spottedJob.getPartnerCertificate().getId().v())) {
                try {
                    addJob(new SyncClientJob());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (unknownJob instanceof CommitJob) {
            syncHandler.commitJob((CommitJob) unknownJob);
        } else if (unknownJob instanceof Job.ConnectionAuthenticatedJob) {
            Job.ConnectionAuthenticatedJob authenticatedJob = (Job.ConnectionAuthenticatedJob) unknownJob;
            if (authenticatedJob.getPartnerCertificate().getId().v().equals(driveSettings.getClientSettings().getServerCertId())) {
                N.r(() -> addJob(new CommitJob(true)));
            }
        } else if (unknownJob instanceof SyncClientJob) {
            N.r(() -> syncHandler.syncFromServer(((SyncClientJob) unknownJob).getNewVersion()));
        }
        return false;
    }

    private void updateHashes(Set<String> hashes) {
        FsDao fsDao = driveDatabaseManager.getFsDao();
        StageDao stageDao = driveDatabaseManager.getStageDao();
        TransferDao transferDao = driveDatabaseManager.getTransferDao();
        fsDao.lockWrite();
        N.forEach(hashes, s -> {
            // if is stage from server or is transfer -> flag as available
            N.forEach(stageDao.getUpdateStageSetsFromServer(), stageSet -> {
                stageDao.devUpdateSyncedByHashSet(stageSet.getId().v(), hashes);
            });
            DriveClientSettingsDetails clientSettings = driveSettings.getClientSettings();
            transferDao.devUpdateAvailableByHashSet(clientSettings.getServerCertId(), clientSettings.getServerServiceUuid(), hashes);
        });
        fsDao.unlockWrite();
    }

    @Override
    public void addJob(Job job) {
        //todo debug
        if (job instanceof Job.ConnectionAuthenticatedJob)
            Lok.debug("MeinDriveClientService.addJob.debug123");
        if (job instanceof SyncClientJob)
            Lok.debug("MeinDriveClientService.addJob.debug345");
        super.addJob(job);
    }


    @Override
    protected ClientSyncHandler initSyncHandler() {
        return new ClientSyncHandler(meinAuthService, this);
    }

    public void syncThisClient() {
        addJob(new SyncClientJob());
    }

    @Override
    public DeferredObject<DeferredRunnable, Exception, Void> startIndexer(DriveDatabaseManager driveDatabaseManager) throws SqlQueriesException {
        super.startIndexer(driveDatabaseManager);
        stageIndexer.setStagingDoneListener(stageSetId -> addJob(new CommitJob()));
        return startIndexerDonePromise;
    }

    @Override
    protected IndexListener createIndexListener() {
        return new IndexListener() {


            @Override
            public void foundDirectory(FsDirectory fsDirectory) {

            }

            @Override
            public void done(Long stageSetId) {
                //addJob(new CommitJob(true));
            }
        };
    }

    @Override
    public void onMeinAuthIsUp() {
        startIndexerDonePromise.done(result -> {
            Lok.debug("MeinDriveClientService.onMeinAuthIsUp");
            N.r(() -> {
                Long serverId = driveSettings.getClientSettings().getServerCertId();
                if (serverId != null) {
                    meinAuthService.connect(serverId).done(result1 -> addJob(new CommitJob()));
                }
            });
        });
    }


    public void onConflicts() {
        Lok.debug("MeinDriveClientService.onConflicts.oj9h034800");
        MeinNotification notification = new MeinNotification(uuid, DriveStrings.Notifications.INTENTION_CONFLICT_DETECTED, "Conflict detected", "here we go");
        meinAuthService.onNotificationFromService(this, notification);
    }

    /**
     * merged {@link StageSet}s might be handled by the {@link de.mein.drive.service.sync.SyncHandler} at some point.
     * eg. it still might show some GUI handling {@link Conflict}s.
     *
     * @param lStageSetId
     * @param rStageSetId
     * @param mergedStageSet
     */
    public void onStageSetsMerged(Long lStageSetId, Long rStageSetId, StageSet mergedStageSet) {
        syncHandler.onStageSetsMerged(lStageSetId, rStageSetId, mergedStageSet);
    }

    public Map<String, ConflictSolver> getConflictSolverMap() {
        return syncHandler.getConflictSolverMap();
    }

    @Override
    public void start() {
        super.start();
        addJob(new CommitJob(true));
    }

    public void onInsufficientSpaceAvailable(Long stageSetId) {
        MeinNotification meinNotification = new MeinNotification(getUuid(), DriveStrings.Notifications.INTENTION_OUT_OF_SPACE, "Out of (Disk) Space", "could not apply changes(" + stageSetId + ") from server");
        meinAuthService.onNotificationFromService(this, meinNotification);
    }
}

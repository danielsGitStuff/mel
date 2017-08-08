package de.mein.drive.service;

import de.mein.DeferredRunnable;
import de.mein.auth.MeinNotification;
import de.mein.auth.MeinStrings;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceMessageHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.index.IndexListener;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.jobs.SyncClientJob;
import de.mein.drive.service.sync.ClientSyncHandler;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.StageSet;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 * Created by xor on 10/21/16.
 */
public class MeinDriveClientService extends MeinDriveService<ClientSyncHandler> {

    private static Logger logger = Logger.getLogger(MeinDriveClientService.class.getName());
    private DriveSyncListener syncListener;

    public MeinDriveClientService(MeinAuthService meinAuthService, File workingDirectory) {
        super(meinAuthService, workingDirectory);
    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newCachedThreadPool(threadFactory);
    }

    @Override
    public void run() {
        super.run();
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
    public void onTransfersDone() {
        if (syncListener != null)
            syncListener.onTransfersDone();
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
                    DriveDetails driveDetails = (DriveDetails) job.getPayLoad();
                    driveDetails.getLastSyncVersion();
                    try {
                        addJob(new SyncClientJob());
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
                    addJob(new SyncClientJob());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (unknownJob instanceof CommitJob) {
            syncHandler.commitJob();
        } else if (unknownJob instanceof Job.ConnectionAuthenticatedJob) {
            Job.ConnectionAuthenticatedJob authenticatedJob = (Job.ConnectionAuthenticatedJob) unknownJob;
            if (authenticatedJob.getPartnerCertificate().getId().v().equals(driveSettings.getClientSettings().getServerCertId())) {
                N.r(() -> addJob(new SyncClientJob()));
            }
        } else if (unknownJob instanceof SyncClientJob) {
            N.r(() -> syncHandler.syncThisClient());
        }
        return false;
    }

    public void addConflictSolver(ConflictSolver conflictSolver) {
        syncHandler.addConflictSolver(conflictSolver);
    }


    public void setSyncListener(DriveSyncListener syncListener) {
        this.syncListener = syncListener;
    }

    private DriveSyncListener getSyncListener() {
        return syncListener;
    }

    @Override
    protected ClientSyncHandler initSyncHandler() {
        return new ClientSyncHandler(meinAuthService, this);
    }

    public void syncThisClient() throws InterruptedException, SqlQueriesException {
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
            public void foundFile(FsFile fsFile) {

            }

            @Override
            public void foundDirectory(FsDirectory fsDirectory) {

            }

            @Override
            public void done(Long stageSetId) {
                addJob(new CommitJob());
            }
        };
    }

    @Override
    public void onMeinAuthIsUp() {
        startIndexerDonePromise.done(result -> {
            System.out.println("MeinDriveClientService.onMeinAuthIsUp");
            N.r(() -> {
                Long serverId = driveSettings.getClientSettings().getServerCertId();
                if (serverId != null) {
                    meinAuthService.connect(serverId).done(result1 -> addJob(new CommitJob()));
                }
            });
        });
    }

    public void onSyncFailed() {
        if (syncListener != null)
            syncListener.onSyncFailed();
    }

    public void onSyncDone() {
        if (syncListener != null)
            syncListener.onSyncDone();
    }

    public void onConflicts(ConflictSolver conflictSolver) {
        System.out.println("MeinDriveClientService.onConflicts.oj9h034800");
        MeinNotification notification = new MeinNotification(uuid, DriveStrings.Notifications.CONFLICT_DETECTED, "Conflict detected", "here we go");
        notification.setContent(conflictSolver);
        meinAuthService.onMessageFromService(this, notification);
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
}

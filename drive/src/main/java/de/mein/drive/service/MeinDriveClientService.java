package de.mein.drive.service;

import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceMessageHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.sync.ClientSyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
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

    private Set<Thread> threads = new HashSet<>();

    @Override
    public void run() {
        threads.add(Thread.currentThread());
        super.run();
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
                    DriveDetails driveDetails = (DriveDetails) job.getPayLoad();
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
                    syncHandler.syncThisClient();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (unknownJob instanceof CommitJob) {
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
            addJob(new CommitJob());
        });
    }

}

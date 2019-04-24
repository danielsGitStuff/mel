package de.mein.drive.service;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.ServicePayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceRequestHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinServiceWorker;
import de.mein.auth.socket.process.transfer.FileTransferDetail;
import de.mein.auth.socket.process.transfer.FileTransferDetailSet;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.IndexListener;
import de.mein.drive.index.Indexer;
import de.mein.drive.index.watchdog.IndexWatchdogListener;
import de.mein.drive.index.watchdog.StageIndexer;
import de.mein.auth.file.AFile;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.tasks.DirectoriesContentTask;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * does everything file (on disk) related
 * Created by xor on 09.07.2016.
 */
public abstract class MeinDriveService<S extends SyncHandler> extends MeinServiceWorker {
    private static Logger logger = Logger.getLogger(MeinDriveService.class.getName());
    protected DriveDatabaseManager driveDatabaseManager;
    protected DriveSettings driveSettings;
    protected N runner = new N(Throwable::printStackTrace);
    protected Indexer indexer;
    protected StageIndexer stageIndexer;
    protected S syncHandler;
    private Wastebin wastebin;
    protected DeferredObject<DeferredRunnable, Exception, Void> startIndexerDonePromise;
    private DriveSyncListener syncListener;


    public void setSyncListener(DriveSyncListener syncListener) {
        this.syncListener = syncListener;
    }

    private DriveSyncListener getSyncListener() {
        return syncListener;
    }

    public void onSyncFailed() {
        if (syncListener != null)
            syncListener.onSyncFailed();
    }

    public void onSyncDone() {
        if (syncListener != null)
            syncListener.onSyncDone();
    }


    public void onTransfersDone() {
        if (syncListener != null)
            syncListener.onTransfersDone();
    }

    public MeinDriveService(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String uuid, DriveSettings driveSettings) {
        super(meinAuthService, workingDirectory, serviceTypeId, uuid);
        this.driveSettings = driveSettings;
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.handleRequest");
        addJob(new ServiceRequestHandlerJob().setRequest(request));
    }

    @Override
    public MeinNotification createSendingNotification() {
        MeinNotification notification = new MeinNotification(uuid,
                DriveStrings.Notifications.INTENTION_PROGRESS
                , "Sending Files", "!")
                .setProgress(0, 0, true);
        return notification;
    }

    @Override
    public void handleMessage(ServicePayload payload, Certificate partnerCertificate, String intent) {
        logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.handleMessage");
        addJob(new ServiceRequestHandlerJob().setPayload(payload).setPartnerCertificate(partnerCertificate).setIntent(intent));
    }

    public DriveSettings getDriveSettings() {
        return driveSettings;
    }

    @Override
    public void connectionAuthenticated(Certificate partnerCertificate) {
        logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.connectionAuthenticated");
        addJob(new Job.ConnectionAuthenticatedJob(partnerCertificate));
    }

    @Override
    public void handleCertificateSpotted(Certificate partnerCertificate) {
        logger.log(Level.FINER, meinAuthService.getName() + ".MeinDriveService.handleCertificateSpotted");
        addJob(new Job.CertificateSpottedJob(partnerCertificate));
    }

    @Override
    protected void workWork(Job unknownJob) throws Exception {
        logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.workWork :)");
        if (!workWorkWork(unknownJob)) {
            if (unknownJob instanceof ServiceRequestHandlerJob) {
                ServiceRequestHandlerJob job = (ServiceRequestHandlerJob) unknownJob;
                if (job.isRequest()) {
                    Request request = job.getRequest();
                    if (request.getIntent() != null && request.getIntent().equals(DriveStrings.INTENT_DRIVE_DETAILS)) {
                        DriveDetails details = driveDatabaseManager.getDriveSettings().getDriveDetails();
                        request.resolve(details);
                    } else if (request.getIntent() != null && request.getIntent().equals(DriveStrings.INTENT_DIRECTORY_CONTENT)) {
                        handleDirectoryContentsRequest(request);
                    } else if (checkIntent(request, DriveStrings.INTENT_SYNC)) {
                        onSyncReceived(request);
                    }
                } else if (job.isMessage()) {
                    logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.workWork.msg");
                    if (job.getIntent() != null && job.getIntent().equals(DriveStrings.INTENT_PLEASE_TRANSFER)) {
                        logger.log(Level.FINEST, "MeinDriveService.workWorkWork: transfer please");
                        handleSending(job.getPartnerCertificate().getId().v(), (FileTransferDetailSet) job.getPayLoad());
                    }
                }
            }
        }
    }

    protected boolean checkIntent(Request request, String expected) {
        String intent = request.getIntent();
        if (intent == null || expected == null)
            return false;
        return intent.equals(expected);
    }

    protected void handleSending(Long partnerCertId, FileTransferDetailSet detailSet) {
        handleSending(partnerCertId, detailSet, true);
    }

    @Override
    public void addJob(Job job) {
        super.addJob(job);
    }

    @Override
    public void suspend() {
        indexer.suspend();
        syncHandler.suspend();
        super.suspend();
    }

    @Override
    public void resume() {
        indexer.resume();
        syncHandler.resume();
        super.resume();
    }

    protected void handleSending(Long partnerCertId, FileTransferDetailSet detailSet, boolean lockFsEntry) {
        //todo synced nicht richtig, wenn hier haltepunkt nach der konfliktlösung
        FsDao fsDao = driveDatabaseManager.getFsDao();
        try {
            if (lockFsEntry)
                fsDao.lockRead();
            for (FileTransferDetail detail : detailSet.getDetails()) {
                AFile wasteFile = wastebin.getByHash(detail.getHash());
                MeinIsolatedFileProcess fileProcess = (MeinIsolatedFileProcess) getIsolatedProcess(partnerCertId, detailSet.getServiceUuid());
                List<FsFile> fsFiles = driveDatabaseManager.getFsDao().getFilesByHash(detail.getHash());
                if (wasteFile != null) {
                    FileTransferDetail mDetail = new FileTransferDetail(wasteFile, detail.getStreamId(), detail.getStart(), detail.getEnd());
                    mDetail.openRead();
                    fileProcess.sendFile(mDetail);
                } else if (fsFiles.size() > 0) {
                    FsFile fsFile = fsFiles.get(0);
                    AFile file = fsDao.getFileByFsFile(driveDatabaseManager.getDriveSettings().getRootDirectory(), fsFile);
                    if (!file.exists()) {
                        file = wastebin.getByHash(detail.getHash());
                    }
                    if (file == null) {
                        fileProcess.sendError(detail);
                    }
                    FileTransferDetail mDetail = new FileTransferDetail(file, detail.getStreamId(), detail.getStart(), detail.getEnd());
                    mDetail.setHash(detail.getHash());
                    mDetail.openRead();
                    fileProcess.sendFile(mDetail);
                } else {
                    //send 404. did not find
                    fileProcess.sendError(detail);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (lockFsEntry)
                fsDao.unlockRead();
        }
    }

    public Indexer getIndexer() {
        return indexer;
    }

    private void handleDirectoryContentsRequest(Request request) throws SqlQueriesException {
        logger.log(Level.FINEST, "MeinDriveService.handleDirectoryContentsRequest");
        DirectoriesContentTask task = (DirectoriesContentTask) request.getPayload();
        for (Long fsId : task.getIds()) {
            FsDirectory fsDirectory = driveDatabaseManager.getFsDao().getDirectoryById(fsId);
            List<GenericFSEntry> content = driveDatabaseManager.getFsDao().getContentByFsDirectory(fsId);
            for (GenericFSEntry genericFSEntry : content) {
                if (genericFSEntry.getIsDirectory().v()) {
                    fsDirectory.addSubDirectory((FsDirectory) genericFSEntry.ins());
                } else {
                    fsDirectory.addFile((FsFile) genericFSEntry.ins());
                }
            }
            task.getResult().add(fsDirectory);
        }
        task.setIDs(null);
        request.resolve(task);
    }

    public void setDriveDatabaseManager(DriveDatabaseManager driveDatabaseManager) {
        this.driveDatabaseManager = driveDatabaseManager;
    }

    protected abstract void onSyncReceived(Request request);

    /**
     * @param unknownJob
     * @return true if handled
     */
    protected abstract boolean workWorkWork(Job unknownJob);

    public DeferredObject<DeferredRunnable, Exception, Void> startIndexer() throws SqlQueriesException {
        this.driveSettings = driveDatabaseManager.getDriveSettings();
        AFile transferDir = driveSettings.getTransferDirectory();
        transferDir.mkdirs();
        AFile wasteDir = AFile.instance(driveSettings.getTransferDirectory(), DriveStrings.WASTEBIN);
        wasteDir.mkdirs();
        this.stageIndexer = new StageIndexer(driveDatabaseManager);
        this.indexer = new Indexer(driveDatabaseManager, IndexWatchdogListener.runInstance(this), createIndexListener());
        this.wastebin = new Wastebin(this);
        this.syncHandler = initSyncHandler();
        this.syncHandler.start();
        indexer.setSyncHandler(syncHandler);
        startIndexerDonePromise = indexer.start();
        return startIndexerDonePromise;
    }

    protected abstract IndexListener createIndexListener();

    protected abstract S initSyncHandler();

    public DriveDatabaseManager getDriveDatabaseManager() {
        return driveDatabaseManager;
    }

    public Promise<List<FsDirectory>, Exception, Void> requestDirectoriesByIds(Set<Long> fsDirIdsToRetrieve, Long certId, String serviceUuid) throws SqlQueriesException, InterruptedException {
        Deferred<List<FsDirectory>, Exception, Void> deferred = new DeferredObject<>();
        Certificate cert = meinAuthService.getCertificateManager().getTrustedCertificateById(certId);
        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId);
        connected.done(meinValidationProcess -> runner.runTry(() -> {
            logger.log(Level.FINEST, "MeinDriveService.requestDirectoriesByIds:::::::::::::::");
            Request<DirectoriesContentTask> answer = meinValidationProcess.request(serviceUuid, new DirectoriesContentTask().setIDs(fsDirIdsToRetrieve));
            answer.done(task -> {
                logger.log(Level.FINEST, "MeinDriveService.requestDirectoriesByIds");
                deferred.resolve(task.getResult());
            });
        }));
        return deferred;
    }

    public Wastebin getWastebin() {
        return wastebin;
    }

    public StageIndexer getStageIndexer() {
        return stageIndexer;
    }

    @Override
    public void onShutDown() {
        super.onShutDown();
        driveDatabaseManager.shutDown();
        if (indexer != null)
            indexer.shutDown();
    }

    @Override
    public void start() {
        Lok.debug("MeinDriveService.start");
        super.start();
    }

    @Override
    public void onCommunicationsDisabled() {

    }

    @Override
    public void onCommunicationsEnabled() {

    }

    @Override
    public void onBootLevel2Finished() {
        Lok.debug("BBBB");
    }

    @Override
    public void onBootLevel1Finished() {
        Lok.debug("AAAA");
    }
}

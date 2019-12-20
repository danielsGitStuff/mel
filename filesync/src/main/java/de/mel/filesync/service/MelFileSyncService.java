package de.mel.filesync.service;

import de.mel.auth.file.IFile;
import de.mel.auth.service.MelAuthService;
import de.mel.filesync.index.InitialIndexConflictHelper;
import de.mel.filesync.index.watchdog.FileWatcherFactory;
import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.List;
import java.util.Set;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.jobs.Job;
import de.mel.auth.jobs.ServiceRequestHandlerJob;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.service.MelServiceWorker;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.transfer.FileTransferDetail;
import de.mel.auth.socket.process.transfer.FileTransferDetailSet;
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.filesync.FileSyncSyncListener;
import de.mel.filesync.data.FileSyncDetails;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.index.IndexListener;
import de.mel.filesync.index.Indexer;
import de.mel.filesync.index.watchdog.StageIndexer;
import de.mel.filesync.service.sync.SyncHandler;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.FsDirectory;
import de.mel.filesync.sql.FsFile;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.tasks.DirectoriesContentTask;
import de.mel.filesync.transfer.FileTransferDetailsPayload;
import de.mel.sql.SqlQueriesException;


/**
 * does everything file (on disk) related
 * Created by xor on 09.07.2016.
 */
public abstract class MelFileSyncService<S extends SyncHandler> extends MelServiceWorker implements PowerManager.IPowerStateListener {
    protected final FileSyncDatabaseManager databaseManager;
    protected FileSyncDatabaseManager fileSyncDatabaseManager;
    protected FileSyncSettings fileSyncSettings;
    protected N runner = new N(Throwable::printStackTrace);
    protected Indexer indexer;
    protected StageIndexer stageIndexer;
    protected S syncHandler;
    protected DeferredObject<DeferredRunnable, Exception, Void> startIndexerDonePromise;
    protected InitialIndexConflictHelper conflictHelper;
    private Wastebin wastebin;
    private FileSyncSyncListener syncListener;


    public MelFileSyncService(MelAuthService melAuthService, File workingDirectory, Long serviceTypeId, String uuid, FileSyncSettings fileSyncSettings, FileSyncDatabaseManager databaseManager) {
        super(melAuthService, workingDirectory, serviceTypeId, uuid, Bootloader.BootLevel.LONG);
        this.fileSyncSettings = fileSyncSettings;
        this.databaseManager = databaseManager;
    }

    public S getSyncHandler() {
        return syncHandler;
    }

    private FileSyncSyncListener getSyncListener() {
        return syncListener;
    }

    public void setSyncListener(FileSyncSyncListener syncListener) {
        this.syncListener = syncListener;
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

    @Override
    public void handleRequest(Request request) throws Exception {
        Lok.debug(melAuthService.getName() + ".MelDriveService.handleRequest");
        addJob(new ServiceRequestHandlerJob().setRequest(request));
    }

    @Override
    public void onStateChanged(PowerManager powerManager) {
        if (isStopped() && powerManager.heavyWorkAllowed())
            resume();
    }

    @Override
    public MelNotification createSendingNotification() {
        MelNotification notification = new MelNotification(uuid,
                FileSyncStrings.Notifications.INTENTION_PROGRESS
                , "Sending Files", "!")
                .setProgress(0, 0, true);
        return notification;
    }

    @Override
    public void handleMessage(ServicePayload payload, Certificate partnerCertificate) {
        Lok.debug(melAuthService.getName() + ".MelDriveService.handleMessage");
        addJob(new ServiceRequestHandlerJob().setPayload(payload).setPartnerCertificate(partnerCertificate).setIntent(payload.getIntent()));
    }

    public FileSyncSettings getFileSyncSettings() {
        return fileSyncSettings;
    }

    @Override
    public void connectionAuthenticated(Certificate partnerCertificate) {
        Lok.debug(melAuthService.getName() + ".MelDriveService.connectionAuthenticated");
        addJob(new Job.ConnectionAuthenticatedJob(partnerCertificate));
    }

    @Override
    public void handleCertificateSpotted(Certificate partnerCertificate) {
        Lok.debug(melAuthService.getName() + ".MelDriveService.handleCertificateSpotted");
        addJob(new Job.CertificateSpottedJob(partnerCertificate));
    }

    @Override
    protected void workWork(Job unknownJob) throws Exception {
        Lok.debug(melAuthService.getName() + ".MelDriveService.workWork :)");
        if (!workWorkWork(unknownJob)) {
            if (unknownJob instanceof ServiceRequestHandlerJob) {
                ServiceRequestHandlerJob job = (ServiceRequestHandlerJob) unknownJob;
                if (job.isRequest()) {
                    Request request = job.getRequest();
                    if (request.hasIntent(FileSyncStrings.INTENT_DRIVE_DETAILS)) {
                        FileSyncDetails details = fileSyncDatabaseManager.getFileSyncSettings().getDriveDetails();
                        request.resolve(details);
                    } else if (request.hasIntent(FileSyncStrings.INTENT_DIRECTORY_CONTENT)) {
                        handleDirectoryContentsRequest(request);
                    } else if (request.hasIntent(FileSyncStrings.INTENT_SYNC)) {
                        onSyncReceived(request);
                    }
                } else if (job.isMessage()) {
                    Lok.debug(melAuthService.getName() + ".MelDriveService.workWork.msg");
                    if (job.getIntent() != null && job.getIntent().equals(FileSyncStrings.INTENT_PLEASE_TRANSFER)) {
                        Lok.debug("MelDriveService.workWorkWork: transfer please");
                        handleSending(job.getPartnerCertificate().getId().v(), (FileTransferDetailsPayload) job.getPayLoad());
                    }
                }
            }
        }
    }

    @Override
    public void addJob(Job job) {
        if (!getReachedBootLevel().equals(getBootLevel())) {
            // turn down all jobs that could possibly hurt if not bootedd up completely
            if (!(job instanceof ServiceRequestHandlerJob)) {
                Lok.error(job.getClass().getSimpleName() + " turnend down. required boot level not reached yet.");
                return;
            }
            ServiceRequestHandlerJob requestHandlerJob = (ServiceRequestHandlerJob) job;
            if (requestHandlerJob.getIntent() == null || !requestHandlerJob.getIntent().equals(FileSyncStrings.INTENT_REG_AS_CLIENT)) {
                Lok.error(job.getClass().getSimpleName() + " turnend down. required boot level not reached yet.");
                return;
            }
        }
        super.addJob(job);
    }

    @Override
    public void stop() {
        if (indexer != null)
            indexer.suspend();
        if (syncHandler != null)
            syncHandler.suspend();
        super.stop();
    }

    @Override
    public void resume() {
        if (indexer != null)
            indexer.resume();
        if (syncHandler != null)
            syncHandler.resume();
        super.resume();
    }

    /**
     * this is called whenever the indexer has done its job.
     * e.g. after resuming or after booting up.
     */
    public abstract void onIndexerDone();

    protected void handleSending(Long partnerCertId, FileTransferDetailsPayload payload) {
        //todo synced nicht richtig, wenn hier haltepunkt nach der konfliktlösung
        FsDao fsDao = fileSyncDatabaseManager.getFsDao();
        Warden warden = P.confine(P.read(fsDao));
        try {

            FileTransferDetailSet detailSet = payload.getFileTransferDetailSet();
            for (FileTransferDetail detail : detailSet.getDetails()) {
                IFile wasteFile = wastebin.getByHash(detail.getHash());
                Promise<MelIsolatedFileProcess, Exception, Void> promise = getIsolatedProcess(MelIsolatedFileProcess.class, partnerCertId, detailSet.getServiceUuid());
                promise.done(fileProcess -> runner.runTry(() -> {
                    List<FsFile> fsFiles = fileSyncDatabaseManager.getFsDao().getFilesByHash(detail.getHash());
                    if (wasteFile != null) {
                        FileTransferDetail mDetail = new FileTransferDetail(wasteFile, detail.getStreamId(), detail.getStart(), detail.getEnd());
                        mDetail.openRead();
                        fileProcess.sendFile(mDetail);
                    } else if (fsFiles.size() > 0) {
                        FsFile fsFile = fsFiles.get(0);
                        IFile file = fsDao.getFileByFsFile(fileSyncDatabaseManager.getFileSyncSettings().getRootDirectory(), fsFile);
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
                }));
//                if (fileProcess == null) {
//                    Lok.error("file transfer process is NULL");
//                }
//                List<FsFile> fsFiles = driveDatabaseManager.getFsDao().getFilesByHash(detail.getHash());
//                if (wasteFile != null) {
//                    FileTransferDetail mDetail = new FileTransferDetail(wasteFile, detail.getStreamId(), detail.getStart(), detail.getEnd());
//                    mDetail.openRead();
//                    fileProcess.sendFile(mDetail);
//                } else if (fsFiles.size() > 0) {
//                    FsFile fsFile = fsFiles.get(0);
//                    AFile file = fsDao.getFileByFsFile(driveDatabaseManager.getDriveSettings().getRootDirectory(), fsFile);
//                    if (!file.exists()) {
//                        file = wastebin.getByHash(detail.getHash());
//                    }
//                    if (file == null) {
//                        fileProcess.sendError(detail);
//                    }
//                    FileTransferDetail mDetail = new FileTransferDetail(file, detail.getStreamId(), detail.getStart(), detail.getEnd());
//                    mDetail.setHash(detail.getHash());
//                    mDetail.openRead();
//                    fileProcess.sendFile(mDetail);
//                } else {
//                    //send 404. did not find
//                    fileProcess.sendError(detail);
//                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            warden.end();
        }
    }

    public Indexer getIndexer() {
        return indexer;
    }

    private void handleDirectoryContentsRequest(Request request) throws SqlQueriesException {
        Lok.debug("MelDriveService.handleDirectoryContentsRequest");
        DirectoriesContentTask task = (DirectoriesContentTask) request.getPayload();
        for (Long fsId : task.getIds()) {
            FsDirectory fsDirectory = fileSyncDatabaseManager.getFsDao().getDirectoryById(fsId);
            List<GenericFSEntry> content = fileSyncDatabaseManager.getFsDao().getContentByFsDirectory(fsId);
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

    protected abstract void onSyncReceived(Request request);

    /**
     * @param unknownJob
     * @return true if handled
     */
    protected abstract boolean workWorkWork(Job unknownJob);

    public DeferredObject<DeferredRunnable, Exception, Void> startIndexer() throws SqlQueriesException {
        this.fileSyncSettings = fileSyncDatabaseManager.getFileSyncSettings();
        IFile transferDir = fileSyncSettings.getTransferDirectory();
        transferDir.mkdirs();
        IFile wasteDir = AbstractFile.instance(fileSyncSettings.getTransferDirectory(), FileSyncStrings.WASTEBIN);
        wasteDir.mkdirs();
        this.stageIndexer = new StageIndexer(fileSyncDatabaseManager);
        this.indexer = new Indexer(fileSyncDatabaseManager, FileWatcherFactory.Companion.getFactory().runInstance(this), createIndexListener());
        if (conflictHelper != null)
            indexer.setConflictHelper(conflictHelper);
        this.wastebin = new Wastebin(this);
        this.syncHandler = initSyncHandler();
        this.syncHandler.start();
        wastebin.setSyncHandler(this.syncHandler);
        indexer.setSyncHandler(syncHandler);
        startIndexerDonePromise = indexer.start();
        return startIndexerDonePromise;
    }

    protected abstract IndexListener createIndexListener();

    protected abstract S initSyncHandler();

    public FileSyncDatabaseManager getFileSyncDatabaseManager() {
        return fileSyncDatabaseManager;
    }


    public Promise<List<FsDirectory>, Exception, Void> requestDirectoriesByIds(Set<Long> fsDirIdsToRetrieve, Long certId, String serviceUuid) throws SqlQueriesException, InterruptedException {
        Deferred<List<FsDirectory>, Exception, Void> deferred = new DeferredObject<>();
        Certificate cert = melAuthService.getCertificateManager().getTrustedCertificateById(certId);
        Promise<MelValidationProcess, Exception, Void> connected = melAuthService.connect(certId);
        connected.done(melValidationProcess -> runner.runTry(() -> {
            Lok.debug("MelDriveService.requestDirectoriesByIds:::::::::::::::");
            Request<DirectoriesContentTask> answer = melValidationProcess.request(serviceUuid, new DirectoriesContentTask().setIDs(fsDirIdsToRetrieve));
            answer.done(task -> {
                Lok.debug("MelDriveService.requestDirectoriesByIds");
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
    public Promise<Void, Void, Void> onShutDown() {
        super.onShutDown();
        if (syncHandler != null)
            syncHandler.onShutDown();
        fileSyncDatabaseManager.shutDown();
        if (indexer != null)
            indexer.shutDown();
        return null;
    }

    @Override
    public void start() {
        Lok.debug("MelDriveService.start");
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
        melAuthService.getPowerManager().addStateListener(this);
        Lok.debug("Resuming downloads");
        syncHandler.resume();
    }

    @Override
    public void onBootLevel1Finished() {
        Lok.debug("AAAA");
    }
}

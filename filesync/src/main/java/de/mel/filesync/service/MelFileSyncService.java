package de.mel.filesync.service;

import de.mel.auth.file.IFile;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.filesync.index.InitialIndexConflictHelper;
import de.mel.filesync.index.watchdog.FileWatcherFactory;
import fun.with.promise.PromiseImpl;
import fun.with.promise.Promise;

import java.io.File;
import java.io.IOException;
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
import de.mel.auth.service.MelServiceWorker;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.transfer.FileTransferDetail;
import de.mel.auth.socket.process.transfer.FileTransferDetailSet;
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock2.P;
import de.mel.filesync.FileSyncSyncListener;
import de.mel.filesync.data.FileSyncDetails;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.index.IndexListener;
import de.mel.filesync.index.Indexer;
import de.mel.filesync.index.watchdog.StageIndexer;
import de.mel.filesync.service.sync.SyncHandler;
import de.mel.filesync.nio.DefaultFsConvenience;
import de.mel.filesync.nio.FsConvenience;
import de.mel.filesync.nio.UnixFsConvenience;
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
    protected FileSyncSettings fileSyncSettings;
    protected N runner = new N(Throwable::printStackTrace);
    protected Indexer indexer;
    protected StageIndexer stageIndexer;
    protected S syncHandler;
    protected PromiseImpl<DeferredRunnable, Exception> startIndexerDonePromise;
    protected InitialIndexConflictHelper conflictHelper;
    private Wastebin wastebin;
    private FileSyncSyncListener syncListener;
    private volatile FsConvenience fsConvenience;
    private final Object fsConvenienceLock = new Object();


    public MelFileSyncService(MelAuthService melAuthService, File workingDirectory, Long serviceTypeId, String uuid, FileSyncSettings fileSyncSettings, FileSyncDatabaseManager databaseManager) {
        super(melAuthService, workingDirectory, serviceTypeId, uuid, Bootloader.BootLevel.LONG);
        this.fileSyncSettings = fileSyncSettings;
        this.databaseManager = databaseManager;
    }

    public S getSyncHandler() {
        return syncHandler;
    }

    /**
     * What the file system of this service's root can do for us, see {@link FsConvenience}.
     */
    public FsConvenience getFsConvenience() {
        FsConvenience result = fsConvenience;
        if (result == null) {
            // not the lock of the service: others hold that while they wait for connections
            synchronized (fsConvenienceLock) {
                if (fsConvenience == null)
                    fsConvenience = createFsConvenience();
                result = fsConvenience;
            }
        }
        return result;
    }

    /**
     * Platforms that know more override this.
     */
    protected FsConvenience createFsConvenience() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? new DefaultFsConvenience() : new UnixFsConvenience();
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
                        FileSyncDetails details = databaseManager.getFileSyncSettings().getDriveDetails();
                        request.resolveOk(details);
                    } else if (request.hasIntent(FileSyncStrings.INTENT_DIRECTORY_CONTENT)) {
                        handleDirectoryContentsRequest(request);
                    } else if (request.hasIntent(FileSyncStrings.INTENT_SYNC)) {
                        onSyncReceived(request);
                    } else if (request.hasIntent(FileSyncStrings.INTENT_PLEASE_TRANSFER)) {
                        Lok.debug("MelDriveService.workWorkWork: transfer please");
                        handleSending(request.getPartnerCertificate().getId().v(), (FileTransferDetailsPayload) request.getPayload())
                                .onDone(queued -> request.resolveOk(null))
                                .onError(request::resolveError);
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

    /**
     * The partner wants files. They are sent over an isolated connection (the one the partner has opened, otherwise one is opened),
     * a file that cannot be sent is announced there as well.
     *
     * @return settled when all files are queued for sending, failed if they cannot be sent at all. The partner is told.
     */
    protected Promise<Void, Exception> handleSending(Long partnerCertId, FileTransferDetailsPayload payload) {
        PromiseImpl<Void, Exception> queued = PromiseImpl.withoutThrowingOnException();
        FileTransferDetailSet detailSet = payload.getFileTransferDetailSet();
        try {
            getIsolatedProcess(MelIsolatedFileProcess.class, partnerCertId, detailSet.getServiceUuid())
                    .onDone(fileProcess -> {
                        try {
                            for (FileTransferDetail detail : detailSet.getDetails())
                                sendRequested(fileProcess, detail);
                            queued.resolveOk(null);
                        } catch (Exception e) {
                            Lok.error("cannot send files to " + partnerCertId + ": " + e);
                            queued.resolveError(e);
                        }
                    })
                    .onError(e -> {
                        Lok.error("cannot send files to " + partnerCertId + ": " + e);
                        queued.resolveError(e);
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            queued.resolveError(e);
        } catch (Exception e) {
            Lok.error("cannot send files to " + partnerCertId + ": " + e);
            queued.resolveError(e);
        }
        return queued;
    }

    /**
     * Sends the part of the file with that hash that the partner asked for, or tells the partner that this is not possible.
     * The partner says where it wants to start, so it can continue a download.
     */
    private void sendRequested(MelIsolatedFileProcess fileProcess, FileTransferDetail detail) throws Exception {
        IFile file = findFileByHash(detail.getHash());
        long start = detail.getStart();
        long end = detail.getEnd();
        if (file == null || start < 0 || start > end || end > file.length()) {
            // we do not have it or not what the partner expects
            fileProcess.sendError(detail);
            return;
        }
        FileTransferDetail mDetail = new FileTransferDetail(file, detail.getStreamId(), start, end);
        mDetail.setHash(detail.getHash());
        try {
            mDetail.openRead();
        } catch (IOException e) {
            Lok.error("cannot read " + file.getAbsolutePath() + ": " + e);
            fileProcess.sendError(detail);
            return;
        }
        fileProcess.sendFile(mDetail);
    }

    /**
     * @return a file with that content: from the wastebin or one of the files in the directory. null if there is none.
     */
    protected IFile findFileByHash(String hash) throws Exception {
        IFile wasteFile = wastebin.getByHash(hash);
        if (wasteFile != null && wasteFile.exists())
            return wasteFile;
        FsDao fsDao = databaseManager.getFsDao();
        for (FsFile fsFile : fsDao.getFilesByHash(hash)) {
            IFile file = fsDao.getFileByFsFile(databaseManager.getFileSyncSettings().getRootDirectory(), fsFile);
            if (file.exists())
                return file;
        }
        return null;
    }

    public Indexer getIndexer() {
        return indexer;
    }

    private void handleDirectoryContentsRequest(Request request) throws SqlQueriesException {
        Lok.debug("MelDriveService.handleDirectoryContentsRequest");
        DirectoriesContentTask task = (DirectoriesContentTask) request.getPayload();
        for (Long fsId : task.getIds()) {
            FsDirectory fsDirectory = databaseManager.getFsDao().getDirectoryById(fsId);
            List<GenericFSEntry> content = databaseManager.getFsDao().getContentByFsDirectory(fsId);
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
        request.resolveOk(task);
    }

    protected abstract void onSyncReceived(Request request);

    /**
     * @param unknownJob
     * @return true if handled
     */
    protected abstract boolean workWorkWork(Job unknownJob);

    public PromiseImpl<DeferredRunnable, Exception> startIndexer() throws SqlQueriesException {
        this.fileSyncSettings = databaseManager.getFileSyncSettings();
        IFile transferDir = fileSyncSettings.getTransferDirectory();
        transferDir.mkdirs();
        IFile wasteDir = AbstractFile.instance(fileSyncSettings.getTransferDirectory(), FileSyncStrings.WASTEBIN);
        wasteDir.mkdirs();
        this.stageIndexer = new StageIndexer(databaseManager);
        this.indexer = new Indexer(databaseManager, FileWatcherFactory.Companion.getFactory().runInstance(this), createIndexListener());
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
        return databaseManager;
    }


    public Promise<List<FsDirectory>, Exception> requestDirectoriesByIds(Set<Long> fsDirIdsToRetrieve, Long certId, String serviceUuid) throws SqlQueriesException, InterruptedException {
        PromiseImpl<List<FsDirectory>, Exception> deferred = PromiseImpl.withoutThrowingOnException();
        Certificate cert = melAuthService.getCertificateManager().getTrustedCertificateById(certId);
        Promise<MelValidationProcess, Exception> connected = melAuthService.connect(certId);
        connected.onDone(melValidationProcess -> runner.runTry(() -> {
            Lok.debug("MelDriveService.requestDirectoriesByIds:::::::::::::::");
            Request<DirectoriesContentTask> answer = melValidationProcess.request(serviceUuid, new DirectoriesContentTask().setIDs(fsDirIdsToRetrieve));
            answer.onDone(task -> {
                Lok.debug("MelDriveService.requestDirectoriesByIds");
                deferred.resolveOk(task.getResult());
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
    public Promise<Void, Void> onShutDown() {
        super.onShutDown();
        if (syncHandler != null)
            syncHandler.onShutDown();
        databaseManager.shutDown();
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

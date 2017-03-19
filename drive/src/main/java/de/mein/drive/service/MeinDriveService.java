package de.mein.drive.service;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceMessageHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinServiceWorker;
import de.mein.auth.socket.process.transfer.FileTransferDetail;
import de.mein.auth.socket.process.transfer.FileTransferDetailSet;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.NoTryRunner;
import de.mein.drive.DriveSettings;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.Indexer;
import de.mein.drive.index.StageIndexer;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.tasks.DirectoriesContentTask;
import de.mein.drive.watchdog.IndexWatchdogListener;
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
    protected NoTryRunner runner = new NoTryRunner(Throwable::printStackTrace);
    protected Indexer indexer;
    protected StageIndexer stageIndexer;
    protected S syncHandler;
    private WasteBin wasteBin;

    public MeinDriveService(MeinAuthService meinAuthService) {
        super(meinAuthService);
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.handleRequest");
        addJob(new ServiceMessageHandlerJob().setRequest(request));
    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {
        logger.log(Level.FINEST, meinAuthService.getName() + ".MeinDriveService.handleMessage");
        addJob(new ServiceMessageHandlerJob().setMessage(payload).setPartnerCertificate(partnerCertificate).setIntent(intent));
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
            if (unknownJob instanceof ServiceMessageHandlerJob) {
                ServiceMessageHandlerJob job = (ServiceMessageHandlerJob) unknownJob;
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
                        handleTransfer(job.getPartnerCertificate().getId().v(), (FileTransferDetailSet) job.getMessage());
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

    protected void handleTransfer(Long partnerCertId, FileTransferDetailSet detailSet) {
        handleTransfer(partnerCertId, detailSet, true);
    }

    protected void handleTransfer(Long partnerCertId, FileTransferDetailSet detailSet, boolean lockFsEntry) {
        FsDao fsDao = driveDatabaseManager.getFsDao();
        try {
            if (lockFsEntry)
                fsDao.lockRead();
            for (FileTransferDetail detail : detailSet.getDetails()) {
                MeinIsolatedFileProcess fileProcess = (MeinIsolatedFileProcess) getIsolatedProcess(partnerCertId, detailSet.getServiceUuid());
                List<FsFile> fsFiles = driveDatabaseManager.getFsDao().getFilesByHash(detail.getHash());
                if (fsFiles.size() > 0) {
                    FsFile fsFile = fsFiles.get(0);
                    File file = fsDao.getFileByFsFile(driveDatabaseManager.getDriveSettings().getRootDirectory(), fsFile);
                    FileTransferDetail mDetail = new FileTransferDetail(file, detail.getStreamId(), detail.getStart(), detail.getEnd());
                    fileProcess.sendFile(mDetail);
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

//
//    /**
//     * handles syncThisClient jobs coming from changes on the disk
//     *
//     * @param fsSyncJob
//     * @return promise, that triggers when everything is staged and the indexer has finished
//     * @throws IOException
//     */
//    protected Promise<Long, Exception, Void> doFsSyncJob(FsSyncJob fsSyncJob) throws IOException, SqlQueriesException {
//        logger.log(Level.FINEST, "MeinDriveServerService.doFsSyncJob");
//        FsDao fsDao = driveDatabaseManager.getFsDao();
//        StageDao stageDao = driveDatabaseManager.getStageDao();
//        fsDao.lockRead();
//        stageDao.lockWrite();
//        StageSet stageSet;
//        try {
//            stageSet = stageDao.createStageSet("fs", null, null);
//            for (String path : fsSyncJob.getPathCollection().getPaths()) {
//                logger.log(Level.FINEST, "MeinDriveServerService.doFsSyncJob: " + path);
//                try {
//                    File f = new File(path);
//                    File parent = f.getParentFile();
//                    FsDirectory fsParent = null;
//                    FsEntry fsEntry = null;
//                    Stage stage;
//                    Stage stageParent = stageDao.getStageByPath(stageSet.getId().v(), parent);
//                    if (stageParent == null) {
//                        // find the actual relating FsEntry of the parent directory
//                        fsParent = fsDao.getFsDirectoryByPath(parent);
//                        // find its relating FsEntry
//                        if (fsParent != null) {
//                            GenericFSEntry genDummy = new GenericFSEntry();
//                            genDummy.getParentId().v(fsParent.getId());
//                            genDummy.getName().v(f.getName());
//                            GenericFSEntry gen = fsDao.getGenericFileByName(genDummy);
//                            if (gen != null)
//                                fsEntry = gen.ins();
//                        } else {
//                            logger.log(Level.FINEST, "MeinDriveServerService.doFsSyncJob.n7u");
//                        }
//                    }
//                    //file might been deleted yet :(
//                    if (!f.exists() && fsEntry == null)
//                        continue;
//                    // stage actual File
//                    stage = new Stage().setName(f.getName()).setIsDirectory(f.isDirectory());
//                    if (fsEntry != null) {
//                        stage.setFsId(fsEntry.getId().v()).setFsParentId(fsEntry.getParentId().v());
//                    }
//                    if (fsParent != null) {
//                        stage.setFsParentId(fsParent.getId().v());
//                    }
//                    // we found everything which already exists in das datenbank
//                    if (stageParent == null) {
//                        stageParent = new Stage().setStageSet(stageSet.getId().v());
//                        if (fsParent == null) {
//                            logger.log(Level.FINEST, "MeinDriveServerService.doFsSyncJob.ken43");
//                            stageParent.setIsDirectory(parent.isDirectory());
//                        } else {
//                            stageParent.setName(fsParent.getName().v())
//                                    .setFsId(fsParent.getId().v())
//                                    .setFsParentId(fsParent.getParentId().v())
//                                    .setStageSet(stageSet.getId().v())
//                                    .setVersion(fsParent.getVersion().v())
//                                    .setIsDirectory(fsParent.getIsDirectory().v());
//                            File exists = fsDao.getFileByFsFile(driveDatabaseManager.getDriveSettings().getRootDirectory(), fsParent);
//                            stageParent.setDeleted(!exists.exists());
//                        }
//                        stageDao.insert(stageParent);
//                    }
//                    stage.setParentId(stageParent.getId());
//                    if (fsParent == null)
//                        stage.setParentId(stageParent.getId());
//                    stage.setStageSet(stageSet.getId().v());
//                    stage.setDeleted(!f.exists());
//                    stageDao.insert(stage);
//                } catch (Exception e) {
//                    System.err.println("MeinDriveServerService.doFsSyncJob: " + path);
//                    e.printStackTrace();
//                }
//            }
//            // done here. set the indexer to work
//            return stageIndexer.indexStage(stageSet.getId().v());
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            //stageDao.deleteStageSet(stageSet.getId().v());
//            fsDao.unlockRead();
//            stageDao.unlockWrite();
//        }
//        return null;
//    }

    protected abstract void onSyncReceived(Request request);

    /**
     * @param unknownJob
     * @return true if handled
     */
    protected abstract boolean workWorkWork(Job unknownJob);

    public void initDatabase(DriveDatabaseManager driveDatabaseManager) throws SqlQueriesException {
        this.driveSettings = driveDatabaseManager.getDriveSettings();
        this.driveDatabaseManager = driveDatabaseManager;
        this.stageIndexer = new StageIndexer(driveDatabaseManager);
        this.indexer = new Indexer(driveDatabaseManager, IndexWatchdogListener.runInstance(this));
        this.wasteBin = new WasteBin(this);
        this.syncHandler = initSyncHandler();
        this.syncHandler.start();
        indexer.setSyncHandler(syncHandler);
        indexer.start();
    }

    protected abstract S initSyncHandler();

    public DriveDatabaseManager getDriveDatabaseManager() {
        return driveDatabaseManager;
    }

    public Promise<List<FsDirectory>, Exception, Void> requestDirectoriesByIds(Set<Long> fsDirIdsToRetrieve, Long certId, String serviceUuid) throws SqlQueriesException, InterruptedException {
        Deferred<List<FsDirectory>, Exception, Void> deferred = new DeferredObject<>();
        Certificate cert = meinAuthService.getCertificateManager().getTrustedCertificateById(certId);
        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId, cert.getAddress().v(), cert.getPort().v(), cert.getCertDeliveryPort().v(), false);
        connected.done(meinValidationProcess -> runner.runTry(() -> {
            logger.log(Level.FINEST, "MeinDriveService.requestDirectoriesByIds:::::::::::::::");
            Request<DirectoriesContentTask> answer = meinValidationProcess.request(serviceUuid, DriveStrings.INTENT_DIRECTORY_CONTENT, new DirectoriesContentTask().setIDs(fsDirIdsToRetrieve));
            answer.done(task -> {
                logger.log(Level.FINEST, "MeinDriveService.requestDirectoriesByIds");
                deferred.resolve(task.getResult());
            });
        }));
        return deferred;
    }

    public WasteBin getWasteBin() {
        return wasteBin;
    }

    public StageIndexer getStageIndexer() {
        return stageIndexer;
    }
}

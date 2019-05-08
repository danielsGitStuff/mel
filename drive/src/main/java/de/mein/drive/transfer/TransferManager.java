package de.mein.drive.transfer;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.auth.MeinNotification;
import de.mein.auth.file.AFile;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.transfer.FileTransferDetail;
import de.mein.auth.socket.process.transfer.FileTransferDetailSet;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.Eva;
import de.mein.auth.tools.N;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.Indexer;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.Wastebin;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.TransferDetails;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.TransferDao;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by xor on 12/16/16.
 */
public class TransferManager extends DeferredRunnable {
    private static final int FILE_REQUEST_LIMIT_PER_CONNECTION = 30;
    private final TransferDao transferDao;
    private final MeinAuthService meinAuthService;
    private final MeinDriveService meinDriveService;
    private final Indexer indexer;
    private final SyncHandler syncHandler;
    private final Wastebin wastebin;
    private final FsDao fsDao;
    private Future<?> future;
    private RWLock lock = new RWLock();
    //private TransferDetails currentTransfer;
    private File transferDir;
    private Map<String, MeinNotification> activeTransfers;
    private ReentrantLock activeTransfersLock = new ReentrantLock();

    public TransferManager(MeinAuthService meinAuthService, MeinDriveService meinDriveService, TransferDao transferDao, Wastebin wastebin, SyncHandler syncHandler) {
        this.transferDao = transferDao;
        this.meinDriveService = meinDriveService;
        this.meinAuthService = meinAuthService;
        this.indexer = meinDriveService.getIndexer();
        this.syncHandler = syncHandler;
        this.wastebin = wastebin;
        this.fsDao = meinDriveService.getDriveDatabaseManager().getFsDao();
    }

    @Override
    public void onShutDown() {
        this.activeTransfersLock.lock();
        N.forEachAdvIgnorantly(this.activeTransfers, (stoppable, index, s, meinNotification) -> meinNotification.cancel());
        this.activeTransfers.clear();
        this.activeTransfersLock.unlock();
        meinAuthService.getPowerManager().releaseWakeLock(this);
        this.lock.unlockWrite().unlockRead();
    }

    private String activeTransferKey(TransferDetails details) {
        String key = details.getCertId().v() + "." + details.getServiceUuid().v();
        return key;
    }

    @Override
    public void suspend() {
        super.suspend();
        lock.unlockWrite();
    }

    public void resume() {
        start();
    }

    @Override
    public void runImpl() {
        String transferDirPath = meinDriveService.getDriveSettings().getRootDirectory().getPath() + File.separator + DriveStrings.TRANSFER_DIR;
        transferDir = new File(transferDirPath);
        transferDir.mkdirs();
        activeTransfers = new HashMap<>();
        while (!Thread.currentThread().isInterrupted() && !isStopped()) {
            try {
                Lok.debug("TransferManager.RUN");
                // these only contain certId and serviceUuid
                List<TransferDetails> groupedTransferSets = transferDao.getTwoTransferSets();
                // check if groupedTransferSets are active yet
                if (groupedTransferSets.size() == 0 || allTransferSetsAreActive(groupedTransferSets)) {
                    Lok.debug("TransferManager.WAIT");
                    meinDriveService.onTransfersDone();
                    lock.lockWrite();
                } else {
                    for (TransferDetails groupedTransferSet : groupedTransferSets) {
                        Lok.debug("TransferManager.run.22222");
                        // skip if already active
                        activeTransfersLock.lock();
                        if (activeTransfers.containsKey(activeTransferKey(groupedTransferSet))) {
                            activeTransfersLock.unlock();
                            continue;
                        }
                        activeTransfersLock.unlock();
                        // todo ask Wastebin for files
                        wastebin.restoreFsFiles(syncHandler);
                        // todo ask FS for files
                        try {
                            fsDao.lockWrite();
                            List<String> hashes = fsDao.searchTransfer();
                            for (String hash : hashes) {
                                List<FsFile> fsFiles = fsDao.getFilesByHash(hash);
                                if (fsFiles != null && fsFiles.size() > 0) {
                                    FsFile fsFile = fsFiles.get(0);
                                    AFile file = fsDao.getFileByFsFile(meinDriveService.getDriveSettings().getRootDirectory(), fsFile);
                                    syncHandler.onFileTransferred(file, hash);
                                    transferDao.deleteByHash(hash);
                                }
                            }
                        } catch (Exception e) {
                            throw e;
                        } finally {
                            fsDao.unlockWrite();
                        }
                        //check if files remain
                        DeferredObject<TransferDetails, TransferDetails, Void> processDone = new DeferredObject<>();
                        // when done: set to not active
                        processDone.done(transferDetails -> {
                            finishActiveTransfer(transferDetails);
                        }).fail(transferDetails -> {
                            cancelActiveTransfer(transferDetails);
                        });
                        boolean transfersRemain = transferDao.hasNotStartedTransfers(groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v());
                        if (transfersRemain) {
                            activeTransfersLock.lock();
                            activeTransfers.put(activeTransferKey(groupedTransferSet), null);
                            activeTransfersLock.unlock();
                            // ask the network for files
                            MeinIsolatedFileProcess fileProcess = (MeinIsolatedFileProcess) meinDriveService.getIsolatedProcess(groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v());
                            if (fileProcess != null && fileProcess.isOpen()) {
                                DeferredObject<TransferDetails, TransferDetails, Void> done = retrieveFiles(fileProcess, groupedTransferSet);
                                done.done(result -> processDone.resolve(groupedTransferSet))
                                        .fail(result -> processDone.resolve(groupedTransferSet));
                            } else {
                                DeferredObject<MeinIsolatedFileProcess, Exception, Void> connected = meinAuthService.connectToService(MeinIsolatedFileProcess.class, groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v(), meinDriveService.getUuid(), null, null, null);
                                connected.done(meinIsolatedProcess -> N.r(() -> {
                                            DeferredObject<TransferDetails, TransferDetails, Void> done = retrieveFiles(meinIsolatedProcess, groupedTransferSet);
                                            done.done(result -> processDone.resolve(groupedTransferSet))
                                                    .fail(result -> processDone.resolve(groupedTransferSet));
                                        })
                                ).fail(exc -> {
                                    processDone.reject(groupedTransferSet);
                                    meinDriveService.onSyncFailed();
                                });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
        shutDown();
    }


    private void finishActiveTransfer(TransferDetails transferDetails) {
        activeTransfersLock.lock();
        MeinNotification notification = activeTransfers.remove(activeTransferKey(transferDetails));
        if (notification != null) {
            notification.setTitle("Transfers finished")
                    .setText("!")
                    .finish();
        }
        activeTransfersLock.unlock();
    }

    private void cancelActiveTransfer(TransferDetails transferDetails) {
        activeTransfersLock.lock();
        MeinNotification notification = activeTransfers.remove(activeTransferKey(transferDetails));
        if (notification != null)
            notification.cancel();
        activeTransfersLock.unlock();
    }

    private Map<String, MeinNotification> notActiveTransfers = new HashMap<>();

    public void showProgress() {
        activeTransfersLock.lock();
        try {
            List<TransferDetails> transferSets = transferDao.getTransferSets(null);
            for (TransferDetails transferDetails : transferSets) {
                final String key = activeTransferKey(transferDetails);
                int max = transferDao.count(transferDetails.getCertId().v(), transferDetails.getServiceUuid().v());
                int current = transferDao.countStarted(transferDetails.getCertId().v(), transferDetails.getServiceUuid().v());
                if (activeTransfers.containsKey(key)) {
                    if (notActiveTransfers.containsKey(key)) {
                        MeinNotification notification = notActiveTransfers.remove(key);
                        notification.cancel();
                    }
                    MeinNotification notification = activeTransfers.get(key);
                    if (notification == null) {
                        notification = new MeinNotification(meinDriveService.getUuid(), DriveStrings.Notifications.INTENTION_PROGRESS, "transferring", "");
                        activeTransfers.put(key, notification);
                        notification.setProgress(max, current, false);
                        meinAuthService.onNotificationFromService(meinDriveService, notification);
                    } else {
                        if (max > 0)
                            notification.setProgress(max, current, false);
                        else {
                            notification.cancel();
                            activeTransfers.remove(key);
                        }
                    }
                } else {
                    MeinNotification notification = notActiveTransfers.get(key);
                    if (notification == null) {
                        notification = new MeinNotification(meinDriveService.getUuid(), DriveStrings.Notifications.INTENTION_PROGRESS, "transferring", "");
                        notActiveTransfers.put(key, notification);
                        meinAuthService.onNotificationFromService(meinDriveService, notification);
                    }
                    notification.setProgress(0, 0, true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            activeTransfersLock.unlock();
        }
    }

    private boolean allTransferSetsAreActive(List<TransferDetails> groupedTransferSets) {
        for (TransferDetails details : groupedTransferSets) {
            if (!activeTransfers.containsKey(activeTransferKey(details)))
                return false;
        }
        return true;
    }


    /**
     * starts a new Retriever thread that transfers everything from the other side and then shuts down.
     *
     * @param fileProcess
     * @param strippedTransferDetails
     * @return
     * @throws SqlQueriesException
     * @throws InterruptedException
     */
    private DeferredObject<TransferDetails, TransferDetails, Void> retrieveFiles(MeinIsolatedFileProcess fileProcess, TransferDetails strippedTransferDetails) throws SqlQueriesException, InterruptedException {
        DeferredObject<TransferDetails, TransferDetails, Void> deferred = new DeferredObject<>();
        // TransferRetriever retriever = new TransferRetriever(meinAuthService,meinDriveService, this,transferDao, syncHandler);
        MeinRunnable runnable = new MeinRunnable() {

            private RWLock lock = new RWLock().lockWrite();

            private void countDown(AtomicInteger countDown) {
                showProgress();
                int count = countDown.decrementAndGet();
                if (count == 0)
                    lock.unlockWrite();
            }

            @Override
            public String getRunnableName() {
                return "Retriever for: " + meinAuthService.getName() + "/" + strippedTransferDetails.getCertId().v() + "/" + strippedTransferDetails.getServiceUuid().v();
            }

            @Override
            public void run() {
                try {
                    List<TransferDetails> transfers = transferDao.getNotStartedTransfers(strippedTransferDetails.getCertId().v(), strippedTransferDetails.getServiceUuid().v(), FILE_REQUEST_LIMIT_PER_CONNECTION);
                    meinAuthService.getPowerManager().wakeLock(this);
                    while (transfers.size() > 0) {
                        AtomicInteger countDown = new AtomicInteger(transfers.size());

                        FileTransferDetailsPayload payLoad = new FileTransferDetailsPayload();
                        FileTransferDetailSet detailSet = new FileTransferDetailSet();
                        payLoad.setFileTransferDetailSet(detailSet);
                        detailSet.setServiceUuid(meinDriveService.getUuid());
                        for (TransferDetails transferDetails : transfers) {
                            transferDao.setStarted(transferDetails.getId().v(), true);
                            transferDetails.getStarted().v(true);
                            AFile target = AFile.instance(meinDriveService.getDriveSettings().getTransferDirectory(), transferDetails.getHash().v());
                            FileTransferDetail fileTransferDetail = new FileTransferDetail(target, new Random().nextInt(), 0L, transferDetails.getSize().v())
                                    .setHash(transferDetails.getHash().v())
                                    .setTransferDoneListener(fileTransferDetail1 -> N.r(() -> {
                                        transferDao.delete(transferDetails.getId().v());
                                        countDown(countDown);
                                        syncHandler.onFileTransferred(target, transferDetails.getHash().v());
                                    }))
                                    .setTransferFailedListener(fileTransferDetail1 -> N.r(() -> {
                                        transferDao.delete(transferDetails.getId().v());
                                        countDown(countDown);
                                        syncHandler.onFileTransferFailed(transferDetails.getHash().v());
                                    }))
                                    .setTransferProgressListener(fileTransferDetail1 -> N.r(() ->
                                            transferDao.updateTransferredBytes(transferDetails.getId().v(), transferDetails.getTransferred().v())
                                    ));
                            Lok.debug("TransferManager.retrieveFiles.add.transfer: " + fileTransferDetail.getStreamId());
                            fileProcess.addFilesReceiving(fileTransferDetail);
                            detailSet.add(fileTransferDetail);
                        }
                        showProgress();
                        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(strippedTransferDetails.getCertId().v());
                        connected.done(validationProcess -> N.r(() -> {
                            payLoad.setIntent(DriveStrings.INTENT_PLEASE_TRANSFER);
                            validationProcess.message(strippedTransferDetails.getServiceUuid().v(), payLoad);
                        })).fail(result -> N.r(() -> {
                            Lok.debug("TransferManager.retrieveFiles.48nf49");
                            deferred.reject(strippedTransferDetails);
                        }));
                        //wait until current batch is transferred
                        lock.lockWrite();
                        transfers = transferDao.getNotStartedTransfers(strippedTransferDetails.getCertId().v(), strippedTransferDetails.getServiceUuid().v(), FILE_REQUEST_LIMIT_PER_CONNECTION);
                    }
                    deferred.resolve(strippedTransferDetails);
                    meinAuthService.getPowerManager().releaseWakeLock(this);
                } catch (Exception e) {
                    deferred.reject(strippedTransferDetails);
                    meinAuthService.getPowerManager().releaseWakeLock(this);
                }
            }
        };
        meinDriveService.execute(runnable);
        return deferred;
    }

    public void research() {
        Eva.eva((eva, count) -> {
            Lok.error(count);
            if (count == 8)
                Lok.warn("debug");
        });
        N.r(() -> N.readSqlResourceIgnorantly(transferDao.getUnnecessaryTransfers(), (sqlResource, transferDetails) -> {
            //todo debug
            if (transferDetails.getHash().equalsValue("238810397cd86edae7957bca350098bc"))
                Lok.warn("debug");
            transferDao.flagDeleted(transferDetails.getId().v(), true);
            MeinIsolatedFileProcess fileProcess = (MeinIsolatedFileProcess) meinDriveService.getIsolatedProcess(transferDetails.getCertId().v(), transferDetails.getServiceUuid().v());
            if (fileProcess != null) {
                fileProcess.cancelByHash(transferDetails.getHash().v());
            }
            cancelActiveTransfer(transferDetails);
        }));
        lock.unlockWrite();
    }

    public void start() {
        meinDriveService.execute(this);
        this.lock.unlockWrite();
    }

    public void createTransfer(TransferDetails transfer) throws SqlQueriesException {
        transferDao.insert(transfer);
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName() + "/" + meinDriveService.getUuid();
    }
}

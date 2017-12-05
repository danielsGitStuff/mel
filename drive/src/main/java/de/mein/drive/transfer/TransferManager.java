package de.mein.drive.transfer;

import de.mein.DeferredRunnable;
import de.mein.MeinRunnable;
import de.mein.auth.MeinNotification;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.transfer.FileTransferDetail;
import de.mein.auth.socket.process.transfer.FileTransferDetailSet;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.CountLock;
import de.mein.auth.tools.Eva;
import de.mein.auth.tools.N;
import de.mein.drive.data.DriveSettings;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 12/16/16.
 */
public class TransferManager extends DeferredRunnable {
    private static final int LIMIT_PER_ADDRESS = 2;
    private final TransferDao transferDao;
    private final MeinAuthService meinAuthService;
    private final MeinDriveService meinDriveService;
    private final Indexer indexer;
    private final SyncHandler syncHandler;
    private final Wastebin wastebin;
    private final FsDao fsDao;
    private Future<?> future;
    private CountLock lock = new CountLock();
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

    }

    private String activeTransferKey(TransferDetails details) {
        String key = details.getCertId().v() + "." + details.getServiceUuid().v();
        //todo debug
        if (key.equals("1.d89e2ebc-032b-479c-ba90-b50d48fab01c"))
            System.out.println("TransferManager.activeTransferKey.debugn0jv45jfh9awe");
        return key;
    }

    @Override
    public void runImpl() {
        String transferDirPath = meinDriveService.getDriveSettings().getRootDirectory().getPath() + File.separator + DriveSettings.TRANSFER_DIR;
        transferDir = new File(transferDirPath);
        transferDir.mkdirs();
        activeTransfers = new HashMap<>();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                System.out.println("TransferManager.RUN");
                // these only contain certId and serviceUuid
                List<TransferDetails> groupedTransferSets = transferDao.getTwoTransferSets();
                // check if groupedTransferSets are active yet
                if (groupedTransferSets.size() == 0 || allTransferSetsAreActive(groupedTransferSets)) {
                    System.out.println("TransferManager.WAIT");
                    meinDriveService.onTransfersDone();
                    lock.lock();
                } else {
                    for (TransferDetails groupedTransferSet : groupedTransferSets) {
                        System.out.println("TransferManager.run.2222");
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
                                    File file = fsDao.getFileByFsFile(meinDriveService.getDriveSettings().getRootDirectory(), fsFile);
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
                        notification = new MeinNotification(meinDriveService.getUuid(), DriveStrings.Notifications.INTENTION_PROGRESS, "transferring", "line2");
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
                        notification = new MeinNotification(meinDriveService.getUuid(), DriveStrings.Notifications.INTENTION_PROGRESS, "transferring", "line2");
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
                    final String workingPath = meinDriveService.getDriveSettings().getTransferDirectoryPath() + File.separator;
                    List<TransferDetails> transfers = transferDao.getNotStartedTransfers(strippedTransferDetails.getCertId().v(), strippedTransferDetails.getServiceUuid().v(), LIMIT_PER_ADDRESS);
                    while (transfers.size() > 0) {
                        AtomicInteger countDown = new AtomicInteger(transfers.size());
                        FileTransferDetailSet payLoad = new FileTransferDetailSet().setServiceUuid(meinDriveService.getUuid());
                        for (TransferDetails transferDetails : transfers) {
                            transferDao.setStarted(transferDetails.getId().v(), true);
                            transferDetails.getStarted().v(true);
                            File target = new File(workingPath + transferDetails.getHash().v());
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
                            System.out.println("TransferManager.retrieveFiles.add.transfer: " + fileTransferDetail.getStreamId());
                            fileProcess.addFilesReceiving(fileTransferDetail);
                            payLoad.add(fileTransferDetail);
                        }
                        showProgress();
                        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(strippedTransferDetails.getCertId().v());
                        connected.done(validationProcess -> N.r(() -> {
                            validationProcess.message(strippedTransferDetails.getServiceUuid().v(), DriveStrings.INTENT_PLEASE_TRANSFER, payLoad);
                        })).fail(result -> N.r(() -> {
                            System.out.println("TransferManager.retrieveFiles.48nf49");
                            deferred.reject(strippedTransferDetails);
                        }));
                        //wait until current batch is transferred
                        lock.lockWrite();
                        transfers = transferDao.getNotStartedTransfers(strippedTransferDetails.getCertId().v(), strippedTransferDetails.getServiceUuid().v(), LIMIT_PER_ADDRESS);
                    }
                    deferred.resolve(strippedTransferDetails);
                } catch (Exception e) {
                    deferred.reject(strippedTransferDetails);
                }
            }
        };
        Eva.eva((eva, count) -> {
            eva.print("appendix");
        });
        meinDriveService.execute(runnable);
        return deferred;
    }

    public void research() {
        N.r(() -> N.readSqlResourceIgnorantly(transferDao.getUnnecessaryTransfers(), (sqlResource, transferDetails) -> {
            transferDao.flagDeleted(transferDetails.getId().v(), true);
            MeinIsolatedFileProcess fileProcess = (MeinIsolatedFileProcess) meinDriveService.getIsolatedProcess(transferDetails.getCertId().v(), transferDetails.getServiceUuid().v());
            if (fileProcess != null) {
                fileProcess.cancelByHash(transferDetails.getHash().v());
            }
            cancelActiveTransfer(transferDetails);

        }));
        lock.unlock();
    }

    public void start() {
        meinDriveService.execute(this);
    }

    public void createTransfer(TransferDetails transfer) throws SqlQueriesException {
        transferDao.insert(transfer);
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName() + "/" + meinDriveService.getUuid();
    }
}

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
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.Indexer;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.Wastebin;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.DbTransferDetails;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.TransferState;
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
public class TransferManager extends DeferredRunnable implements MeinIsolatedProcess.IsolatedProcessListener {
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
    private Map<Long, MeinIsolatedFileProcess> activeFileProcesses;

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

    private String activeTransferKey(DbTransferDetails details) {
        String key = details.getCertId().v() + "." + details.getServiceUuid().v();
        return key;
    }

    @Override
    public void stop() {
        Lok.debug("stop");
        super.stop();
        lock.unlockWrite();
    }

    public void resume() {
        Lok.debug("resume");
        this.lock.unlockWrite();
    }

    @Override
    public void runImpl() {
        String transferDirPath = meinDriveService.getDriveSettings().getRootDirectory().getPath() + File.separator + DriveStrings.TRANSFER_DIR;
        transferDir = new File(transferDirPath);
        transferDir.mkdirs();
        activeTransfers = new HashMap<>();
        activeFileProcesses = new HashMap<>();
        while (!Thread.currentThread().isInterrupted() && !isStopped()) {
            try {
                Lok.debug("TransferManager.RUN");
                // these only contain certId and serviceUuid
                List<DbTransferDetails> groupedTransferSets = transferDao.getTwoTransferSets();
                // check if groupedTransferSets are active yet
                activeTransfersLock.lock();
                if ((groupedTransferSets.size() == 0 || allTransferSetsAreActive(groupedTransferSets)) && activeTransfers.size() == 0) {
                    Lok.debug("TransferManager.WAIT");
//                    meinDriveService.onTransfersDone();
                    lock.lockWrite();
                } else {
                    for (DbTransferDetails groupedTransferSet : groupedTransferSets) {
                        Lok.debug("TransferManager.run.22222");
                        // skip if already active
//                        activeTransfersLock.lock();
                        if (activeTransfers.containsKey(activeTransferKey(groupedTransferSet))) {
//                            activeTransfersLock.unlock();
                            continue;
                        }
//                        activeTransfersLock.unlock();
                        // todo ask Wastebin for files
                        wastebin.restoreFsFiles();
                        // todo ask FS for files
                        Transaction transaction = T.lockingTransaction(fsDao);
                        try {
                            List<String> hashes = fsDao.searchTransfer();
                            for (String hash : hashes) {
                                List<FsFile> fsFiles = fsDao.getFilesByHash(hash);
                                if (fsFiles != null && fsFiles.size() > 0) {
                                    FsFile fsFile = fsFiles.get(0);
                                    AFile file = fsDao.getFileByFsFile(meinDriveService.getDriveSettings().getRootDirectory(), fsFile);
                                    syncHandler.onFileTransferred(file, hash, transaction);
                                    transferDao.deleteByHash(hash);
                                }
                            }
                        } catch (Exception e) {
                            throw e;
                        } finally {
                            transaction.end();
                        }
                        // check if files remain
                        boolean transfersRemain = transferDao.hasNotStartedTransfers(groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v());
                        if (transfersRemain) {
                            activeTransfers.put(activeTransferKey(groupedTransferSet), null);
                            // ask the network for files
                            retrieveFromCert(groupedTransferSet);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                break;
            } finally {
                activeTransfersLock.unlock();

            }
        }
        shutDown();
    }

    private void retrieveFromCert(DbTransferDetails groupedTransferSet) throws InterruptedException, SqlQueriesException {
        Promise<MeinIsolatedFileProcess, Exception, Void> connected = meinDriveService.getIsolatedProcess(MeinIsolatedFileProcess.class, groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v());
        connected.done(meinIsolatedProcess -> N.r(() -> {
                    // store the isolated process for later
                    activeTransfersLock.lock();
                    activeFileProcesses.put(groupedTransferSet.getCertId().v(), meinIsolatedProcess);
                    activeTransfersLock.unlock();
                    meinIsolatedProcess.addIsolatedProcessListener(this);

                    DeferredObject<DbTransferDetails, DbTransferDetails, Void> done = retrieveFiles(meinIsolatedProcess, groupedTransferSet);
                    done.done(result -> finishActiveTransfer(groupedTransferSet))
                            .fail(result -> cancelActiveTransfer(groupedTransferSet));
                })
        ).fail(exc -> {
            cancelActiveTransfer(groupedTransferSet);
            meinDriveService.onSyncFailed();
        });
    }


    private void finishActiveTransfer(DbTransferDetails dbTransferDetails) {
        activeTransfersLock.lock();
        MeinNotification notification = activeTransfers.remove(activeTransferKey(dbTransferDetails));
        if (notification != null) {
            notification.setTitle("Transfers finished")
                    .setText("!")
                    .finish();
        }
        if (activeTransfers.isEmpty())
            meinDriveService.onTransfersDone();
        activeTransfersLock.unlock();
    }

    private void cancelActiveTransfer(DbTransferDetails dbTransferDetails) {
        activeTransfersLock.lock();
        MeinNotification notification = activeTransfers.remove(activeTransferKey(dbTransferDetails));
        if (notification != null)
            notification.cancel();
        activeTransfersLock.unlock();
    }

    private Map<String, MeinNotification> notActiveTransfers = new HashMap<>();

    public void showProgress() {
        activeTransfersLock.lock();
        try {
            List<DbTransferDetails> transferSets = transferDao.getTransferSets(null);
            for (DbTransferDetails dbTransferDetails : transferSets) {
                final String key = activeTransferKey(dbTransferDetails);
                Long max = transferDao.count(dbTransferDetails.getCertId().v(), dbTransferDetails.getServiceUuid().v());
                int current = transferDao.countStarted(dbTransferDetails.getCertId().v(), dbTransferDetails.getServiceUuid().v());
                if (activeTransfers.containsKey(key)) {
                    if (notActiveTransfers.containsKey(key)) {
                        MeinNotification notification = notActiveTransfers.remove(key);
                        notification.cancel();
                    }
                    MeinNotification notification = activeTransfers.get(key);
                    if (notification == null) {
                        notification = new MeinNotification(meinDriveService.getUuid(), DriveStrings.Notifications.INTENTION_PROGRESS, "transferring", "");
                        activeTransfers.put(key, notification);
                        notification.setProgress(Integer.parseInt(max.toString()), current, false);
                        meinAuthService.onNotificationFromService(meinDriveService, notification);
                    } else {
                        if (max > 0)
                            notification.setProgress(Integer.parseInt(max.toString()), current, false);
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

    private boolean allTransferSetsAreActive(List<DbTransferDetails> groupedTransferSets) {
        for (DbTransferDetails details : groupedTransferSets) {
            if (!activeTransfers.containsKey(activeTransferKey(details)))
                return false;
        }
        return true;
    }


    /**
     * starts a new Retriever thread that transfers everything from the other side and then shuts down.
     *
     * @param fileProcess
     * @param strippedDbTransferDetails
     * @return
     */
    private DeferredObject<DbTransferDetails, DbTransferDetails, Void> retrieveFiles(MeinIsolatedFileProcess fileProcess, DbTransferDetails strippedDbTransferDetails) {
        DeferredObject<DbTransferDetails, DbTransferDetails, Void> deferred = new DeferredObject<>();
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
                return "Retriever for: " + meinAuthService.getName() + "/" + strippedDbTransferDetails.getCertId().v() + "/" + strippedDbTransferDetails.getServiceUuid().v();
            }

            @Override
            public void run() {
                try {
                    List<DbTransferDetails> transfers = transferDao.getNotStartedTransfers(strippedDbTransferDetails.getCertId().v(), strippedDbTransferDetails.getServiceUuid().v(), FILE_REQUEST_LIMIT_PER_CONNECTION);
                    meinAuthService.getPowerManager().wakeLock(this);
                    while (transfers.size() > 0) {
                        AtomicInteger countDown = new AtomicInteger(transfers.size());

                        FileTransferDetailsPayload payLoad = new FileTransferDetailsPayload();
                        FileTransferDetailSet detailSet = new FileTransferDetailSet();
                        payLoad.setFileTransferDetailSet(detailSet);
                        detailSet.setServiceUuid(meinDriveService.getUuid());
                        for (DbTransferDetails transferDetails : transfers) {
                            transferDetails.getState().v(TransferState.RUNNING);
                            AFile target = AFile.instance(meinDriveService.getDriveSettings().getTransferDirectory(), transferDetails.getHash().v());
                            FileTransferDetail fileTransferDetail = new FileTransferDetail(target, new Random().nextInt(), 0L, transferDetails.getSize().v())
                                    .setHash(transferDetails.getHash().v())
                                    .setTransferDoneListener(fileTransferDetail1 -> N.r(() -> {
                                        transferDao.updateState(transferDetails.getId().v(), TransferState.DONE);
//                                        transferDao.delete(transferDetails.getId().v());
                                        countDown(countDown);
                                        Transaction transaction = T.lockingTransaction(fsDao);
                                        syncHandler.onFileTransferred(target, transferDetails.getHash().v(), transaction);
                                        transaction.end();
                                    }))
                                    .setTransferFailedListener(fileTransferDetail1 -> N.r(() -> {
                                        transferDao.updateState(transferDetails.getId().v(), TransferState.SUSPENDED);
//                                        transferDao.delete(transferDetails.getId().v());
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
                        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(strippedDbTransferDetails.getCertId().v());
                        connected.done(validationProcess -> N.r(() -> {
                            payLoad.setIntent(DriveStrings.INTENT_PLEASE_TRANSFER);
                            validationProcess.message(strippedDbTransferDetails.getServiceUuid().v(), payLoad);
                        })).fail(result -> N.r(() -> {
                            Lok.debug("TransferManager.retrieveFiles.48nf49");
                            deferred.reject(strippedDbTransferDetails);
                        }));
                        //wait until current batch is transferred
                        lock.lockWrite();
                        transfers = transferDao.getNotStartedTransfers(strippedDbTransferDetails.getCertId().v(), strippedDbTransferDetails.getServiceUuid().v(), FILE_REQUEST_LIMIT_PER_CONNECTION);
                    }
                    deferred.resolve(strippedDbTransferDetails);
                } catch (Exception e) {
                    deferred.reject(strippedDbTransferDetails);
                } finally {
                    meinAuthService.getPowerManager().releaseWakeLock(this);
                }
            }
        };
        meinDriveService.execute(runnable);
        return deferred;
    }

    public void research() {
        N.r(() -> N.readSqlResourceIgnorantly(transferDao.getUnnecessaryTransfers(), (sqlResource, transferDetails) -> {
            transferDao.flagDeleted(transferDetails.getId().v(), true);
            Promise<MeinIsolatedFileProcess, Exception, Void> connected = meinDriveService.getIsolatedProcess(MeinIsolatedFileProcess.class, transferDetails.getCertId().v(), transferDetails.getServiceUuid().v());
            connected.done(result -> N.oneLine(() -> result.cancelByHash(transferDetails.getHash().v())));
            cancelActiveTransfer(transferDetails);
        }));
        lock.unlockWrite();
    }

    public void start() {
        meinDriveService.execute(this);
        this.lock.unlockWrite();
    }

    public void createTransfer(DbTransferDetails transfer) throws SqlQueriesException {
        transferDao.insert(transfer);
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName() + "/" + meinDriveService.getUuid();
    }

    @Override
    public void onIsolatedProcessEnds(MeinIsolatedProcess isolatedProcess) {
        Lok.debug("");
    }
}

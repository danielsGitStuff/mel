package de.mein.drive.transfer;

import de.mein.MeinThread;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.transfer.FileTransferDetail;
import de.mein.auth.socket.process.transfer.FileTransferDetailSet;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.NoTryRunner;
import de.mein.drive.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.Indexer;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.SyncHandler;
import de.mein.drive.sql.TransferDetails;
import de.mein.drive.sql.dao.TransferDao;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 12/16/16.
 */
public class TransferManager implements Runnable {
    private static final int LIMIT_PER_ADDRESS = 2;
    private static Logger logger = Logger.getLogger(TransferManager.class.getName());
    private final TransferDao transferDao;
    private final MeinAuthService meinAuthService;
    private final MeinDriveService meinDriveService;
    private final Indexer indexer;
    private final SyncHandler syncHandler;
    private Future<?> future;
    private RWLock lock = new RWLock();
    //private TransferDetails currentTransfer;
    private File transferDir;

    public TransferManager(MeinAuthService meinAuthService, MeinDriveService meinDriveService, TransferDao transferDao, SyncHandler syncHandler) {
        this.transferDao = transferDao;
        this.lock.lockWrite();
        this.meinDriveService = meinDriveService;
        this.meinAuthService = meinAuthService;
        this.indexer = meinDriveService.getIndexer();
        this.syncHandler = syncHandler;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void run() {
        String transferDirPath = meinDriveService.getDriveSettings().getRootDirectory().getPath() + File.separator + DriveSettings.TRANSFER_DIR;
        transferDir = new File(transferDirPath);
        transferDir.mkdirs();
        while (!Thread.currentThread().isInterrupted()) {
            NoTryRunner.run(() -> {
                logger.log(Level.FINER, "TransferManager.RUN");
                // these only contain certId and serviceUuid
                List<TransferDetails> groupedTransferSets = transferDao.getTwoTransferSets();
                if (groupedTransferSets.size() == 0) {
                    logger.log(Level.FINER, "TransferManager.WAIT");
                    lock.lockWrite();
                } else {
                    for (TransferDetails groupedTransferSet : groupedTransferSets) {
                        logger.log(Level.FINER, "TransferManager.run.2222");
                        MeinIsolatedFileProcess fileProcess = (MeinIsolatedFileProcess) meinDriveService.getIsolatedProcess(groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v());
                        if (fileProcess != null) {
                            dings(fileProcess, groupedTransferSet);
                        } else {
                            DeferredObject<MeinIsolatedFileProcess, Exception, Void> deferred = meinAuthService.connectToService(MeinIsolatedFileProcess.class, groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v(), meinDriveService.getUuid(), null, null, null);
                            deferred.done(meinIsolatedProcess -> NoTryRunner.run(() -> {
                                        dings(meinIsolatedProcess, groupedTransferSet);
                                    })
                            );
                        }
                    }
                    lock.lockWrite();
                }
            });
        }
    }

    private void dings(MeinIsolatedFileProcess fileProcess, TransferDetails strippedTransferDetails) throws SqlQueriesException, InterruptedException {
        final String workingPath = meinDriveService.getDriveSettings().getTransferDirectoryPath() + File.separator;
        List<TransferDetails> transfers = transferDao.getTransfers(strippedTransferDetails.getCertId().v(), strippedTransferDetails.getServiceUuid().v(), LIMIT_PER_ADDRESS);
        AtomicInteger countDown = new AtomicInteger(transfers.size());
        FileTransferDetailSet payLoad = new FileTransferDetailSet().setServiceUuid(meinDriveService.getUuid());
        for (TransferDetails transferDetails : transfers) {
            File target = new File(workingPath + DriveStrings.WASTEBIN + File.separator + transferDetails.getHash().v());
            FileTransferDetail fileTransferDetail = new FileTransferDetail(target, new Random().nextInt(), 0L, transferDetails.getSize().v())
                    .setHash(transferDetails.getHash().v())
                    .setTransferDoneListener(fileTransferDetail1 -> NoTryRunner.run(() -> {
                        transferDao.delete(transferDetails.getId().v());
                        int count = countDown.decrementAndGet();
                        if (count == 0)
                            lock.unlockWrite();
                        syncHandler.onFileTransferred(target, transferDetails.getHash().v());
                    }));
            System.out.println("TransferManager.dings.add.transfer: " + fileTransferDetail.getStreamId());
            fileProcess.addFilesReceiving(fileTransferDetail);
            payLoad.add(fileTransferDetail);
        }
        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(strippedTransferDetails.getCertId().v());
        connected.done(validationProcess -> NoTryRunner.run(() -> {
            validationProcess.message(strippedTransferDetails.getServiceUuid().v(), DriveStrings.INTENT_PLEASE_TRANSFER, payLoad);
        }));
    }

    public void research() {
        lock.unlockWrite();
    }

    public void start() {
        new MeinThread(this).start();
        //this.future = Executor.startCached(this);
    }

    public void createTransfer(TransferDetails transfer) throws SqlQueriesException {
        transferDao.insert(transfer);
    }
}

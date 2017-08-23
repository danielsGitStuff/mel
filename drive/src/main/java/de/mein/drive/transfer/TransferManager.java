package de.mein.drive.transfer;

import de.mein.DeferredRunnable;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.transfer.FileTransferDetail;
import de.mein.auth.socket.process.transfer.FileTransferDetailSet;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.drive.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.Indexer;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.service.WasteBin;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.TransferDetails;
import de.mein.drive.sql.dao.FsDao;
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
public class TransferManager extends DeferredRunnable {
    private static final int LIMIT_PER_ADDRESS = 2;
    private static Logger logger = Logger.getLogger(TransferManager.class.getName());
    private final TransferDao transferDao;
    private final MeinAuthService meinAuthService;
    private final MeinDriveService meinDriveService;
    private final Indexer indexer;
    private final SyncHandler syncHandler;
    private final WasteBin wasteBin;
    private final FsDao fsDao;
    private Future<?> future;
    private RWLock lock = new RWLock();
    //private TransferDetails currentTransfer;
    private File transferDir;

    public TransferManager(MeinAuthService meinAuthService, MeinDriveService meinDriveService, TransferDao transferDao, WasteBin wasteBin, SyncHandler syncHandler) {
        this.transferDao = transferDao;
        this.lock.lockWrite();
        this.meinDriveService = meinDriveService;
        this.meinAuthService = meinAuthService;
        this.indexer = meinDriveService.getIndexer();
        this.syncHandler = syncHandler;
        this.wasteBin = wasteBin;
        this.fsDao = meinDriveService.getDriveDatabaseManager().getFsDao();
    }

    @Override
    public void onShutDown() {

    }


    @Override
    public void runImpl() {
        String transferDirPath = meinDriveService.getDriveSettings().getRootDirectory().getPath() + File.separator + DriveSettings.TRANSFER_DIR;
        transferDir = new File(transferDirPath);
        transferDir.mkdirs();
        while (!Thread.currentThread().isInterrupted()) {
            N.r(() -> {
                logger.log(Level.FINER, "TransferManager.RUN");
                // these only contain certId and serviceUuid
                List<TransferDetails> groupedTransferSets = transferDao.getTwoTransferSets();
                if (groupedTransferSets.size() == 0) {
                    logger.log(Level.FINER, "TransferManager.WAIT");
                    meinDriveService.onTransfersDone();
                    lock.lockWrite();
                } else {
                    for (TransferDetails groupedTransferSet : groupedTransferSets) {
                        logger.log(Level.FINER, "TransferManager.run.2222");
                        // todo ask WasteBin for files
                        wasteBin.restoreFsFiles(syncHandler);
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
                        // ask the network for files
                        MeinIsolatedFileProcess fileProcess = (MeinIsolatedFileProcess) meinDriveService.getIsolatedProcess(groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v());
                        if (fileProcess != null && fileProcess.isOpen()) {
                            retrieveFiles(fileProcess, groupedTransferSet);
                        } else {
                            DeferredObject<MeinIsolatedFileProcess, Exception, Void> deferred = meinAuthService.connectToService(MeinIsolatedFileProcess.class, groupedTransferSet.getCertId().v(), groupedTransferSet.getServiceUuid().v(), meinDriveService.getUuid(), null, null, null);
                            deferred.done(meinIsolatedProcess -> N.r(() -> {
                                        retrieveFiles(meinIsolatedProcess, groupedTransferSet);
                                    })
                            );
                        }
                    }
                }
            });
        }
    }

    private void countDown(AtomicInteger countDown) {
        int count = countDown.decrementAndGet();
        if (count == 0)
            lock.unlockWrite();
    }

    private void retrieveFiles(MeinIsolatedFileProcess fileProcess, TransferDetails strippedTransferDetails) throws SqlQueriesException, InterruptedException {
        final String workingPath = meinDriveService.getDriveSettings().getTransferDirectoryPath() + File.separator;
        List<TransferDetails> transfers = transferDao.getNotStartedTransfers(strippedTransferDetails.getCertId().v(), strippedTransferDetails.getServiceUuid().v(), LIMIT_PER_ADDRESS);
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
                    }));
            System.out.println("TransferManager.retrieveFiles.add.transfer: " + fileTransferDetail.getStreamId());
            fileProcess.addFilesReceiving(fileTransferDetail);
            payLoad.add(fileTransferDetail);
        }
        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(strippedTransferDetails.getCertId().v());
        connected.done(validationProcess -> N.r(() -> {
            validationProcess.message(strippedTransferDetails.getServiceUuid().v(), DriveStrings.INTENT_PLEASE_TRANSFER, payLoad);
        })).fail(result -> N.r(() -> {
            System.out.println("TransferManager.retrieveFiles.48nf49");
        }));
    }

    public void research() {
        lock.unlockWrite();
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

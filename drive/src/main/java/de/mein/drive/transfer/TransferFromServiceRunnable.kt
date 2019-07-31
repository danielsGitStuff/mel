package de.mein.drive.transfer

import de.mein.Lok
import de.mein.MeinRunnable
import de.mein.auth.file.AFile
import de.mein.auth.socket.process.transfer.FileTransferDetail
import de.mein.auth.socket.process.transfer.FileTransferDetailSet
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess
import de.mein.auth.tools.lock.T
import de.mein.drive.data.DriveStrings
import de.mein.drive.service.MeinDriveService
import de.mein.drive.service.sync.SyncHandler
import de.mein.drive.sql.TransferDetails
import de.mein.drive.sql.TransferState
import de.mein.sql.RWLock
import java.lang.Exception
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class TransferFromServiceRunnable(val tManager: TManager, val fileProcess: MeinIsolatedFileProcess) : MeinRunnable {
    val driveService: MeinDriveService<out SyncHandler> = fileProcess.service as MeinDriveService<out SyncHandler>
    private val transferDao = tManager.transferDao
    private val fsDao = tManager.fsDao
    private val partnerCertId = fileProcess.partnerCertificateId
    private val partnerServiceUuid = fileProcess.partnerServiceUuid
    private var fileCount: Long = 0L
    private var filesDoneCount: Long = 0
    private var stopped = false
    var filesRemain = AtomicLong(0L)
    private val waitLock = RWLock().lockWrite()

    private fun count(): Long {
        fileCount = transferDao.count(partnerCertId, partnerServiceUuid)
        filesDoneCount = transferDao.countDone(partnerCertId, partnerServiceUuid)
        filesRemain.set(fileCount - filesDoneCount)
        return filesRemain.get()
    }

    init {
        count()
    }

    override fun run() {
        try {
            while (!stopped) {
                if (count() > 0) {
                    // we will send this to the partner
                    val payload = FileTransferDetailsPayload()
                    val detailSet = FileTransferDetailSet()
                    detailSet.serviceUuid = driveService.uuid
                    payload.fileTransferDetailSet = detailSet;
                    // prepare for retrieving the files and fill the payload so the partner can send us what we want
                    val transfers: MutableList<TransferDetails> = transferDao.getNotStartedTransfers(partnerCertId, partnerServiceUuid, 30)
                    // countdown: when reaching zero we got to load a new batch
                    val batchCountDown = AtomicInteger(transfers.size)
                    fun decreaseBatchCounter() {
                        val count = batchCountDown.decrementAndGet()
                        if (count == 0) {
                            waitLock.unlockWrite()
                        }
                    }
                    transfers.forEach { dbDetail ->
                        //update state first
                        dbDetail.state.v(TransferState.RUNNING)
                        transferDao.updateState(dbDetail.id.v(), dbDetail.state.v())
                        // find out where to store
                        val target = AFile.instance(driveService.getDriveSettings().getTransferDirectory(), dbDetail.hash.v())
                        val transferDetail = FileTransferDetail(target, Random.nextInt(), 0L, dbDetail.size.v())
                        transferDetail.hash = dbDetail.hash.v()
                        transferDetail.setTransferDoneListener {
                            filesRemain.decrementAndGet()
                            // update states
                            dbDetail.state.v(TransferState.DONE)
                            transferDao.updateState(dbDetail.id.v(), dbDetail.state.v())
                            // tell the sync handler we got a file
                            val transaction = T.lockingTransaction(fsDao)
                            try {
                                driveService.syncHandler.onFileTransferred(target, dbDetail.hash.v(), transaction)
                            } finally {
                                transaction.end()
                            }
                            decreaseBatchCounter()
                        }
                        transferDetail.setTransferFailedListener {
                            filesRemain.decrementAndGet()
                            dbDetail.state.v(TransferState.SUSPENDED)
                            transferDao.updateState(dbDetail.id.v(), dbDetail.state.v())
                            decreaseBatchCounter()
                        }
                        transferDetail.setTransferProgressListener {

                        }
                        detailSet.add(transferDetail)
                        fileProcess.addFilesReceiving(transferDetail)
                    }
                    val connected = driveService.meinAuthService.connect(partnerCertId)
                    connected.done {
                        payload.intent = DriveStrings.INTENT_PLEASE_TRANSFER
                        it.message(partnerServiceUuid, payload)
                        waitLock.lockWrite()
                    }.fail {
                        stopped = true
                        waitLock.unlockWrite()
                    }
//                    waitLock.lockWrite()
                } else {
                    Lok.debug("nothing more to do")
                    stopped = true
                }
                // todo check if any suspended transfers remain and try again
            }
        } catch (e: Exception) {
            Lok.error(e)
        } finally {
            Lok.debug("transfers from $partnerCertId/$partnerServiceUuid finished")
        }
    }

    override fun getRunnableName(): String = "transferring from ${fileProcess.partnerCertificateId}/${fileProcess.partnerServiceUuid}"

}

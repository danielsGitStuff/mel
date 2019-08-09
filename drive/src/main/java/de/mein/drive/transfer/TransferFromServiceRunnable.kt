package de.mein.drive.transfer

import de.mein.Lok
import de.mein.MeinRunnable
import de.mein.auth.MeinNotification
import de.mein.auth.file.AFile
import de.mein.auth.socket.process.transfer.FileTransferDetail
import de.mein.auth.socket.process.transfer.FileTransferDetailSet
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess
import de.mein.auth.tools.lock.T
import de.mein.drive.data.DriveStrings
import de.mein.drive.service.MeinDriveService
import de.mein.drive.service.sync.SyncHandler
import de.mein.drive.sql.DbTransferDetails
import de.mein.drive.sql.TransferState
import de.mein.sql.RWLock
import java.lang.Exception
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class TransferFromServiceRunnable(val tManager: TManager, val fileProcess: MeinIsolatedFileProcess) : MeinRunnable {
    private lateinit var currentDBSet: MutableMap<FileTransferDetail, DbTransferDetails>
    val driveService: MeinDriveService<out SyncHandler> = fileProcess.service as MeinDriveService<out SyncHandler>
    private val transferDao = tManager.transferDao
    private val fsDao = tManager.fsDao
    private val partnerCertId = fileProcess.partnerCertificateId
    private val partnerServiceUuid = fileProcess.partnerServiceUuid
    private var fileCount: Long = 0L
    private var filesDoneCount: Long = 0
    private var stopped = false
    private var notification: MeinNotification? = null
    var filesRemain = AtomicLong(0L)
    private val waitLock = RWLock().lockWrite()

    fun showProgress() {

        try {
            val leftovers = transferDao.getLeftoversByService(partnerCertId, partnerServiceUuid)
            if (leftovers == null) {
                notification?.cancel()
                return
            }
            val title = "Downloading Files: ${leftovers.filesTransferred.v()}/${leftovers.filesTotal.v()}"
            val text = "${leftovers.bytesTransferred.v() / 1024 / 1024}/${leftovers.bytesTotal.v() / 1024 / 1024} mb"

            if (notification == null) {
                notification = MeinNotification(driveService.uuid, DriveStrings.Notifications.INTENTION_PROGRESS, title, text)
                driveService.meinAuthService.onNotificationFromService(driveService, notification)
            }

            val maxInt: Int = (leftovers.bytesTotal.v() / 1024 / 1024).toInt()
            val currentInt: Int = (leftovers.bytesTransferred.v() / 1024 / 1024).toInt()
            notification!!.text = text
            notification!!.title = title
            notification!!.setProgress(maxInt, currentInt, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun count(): Long {
        fileCount = transferDao.count(partnerCertId, partnerServiceUuid)
        filesDoneCount = transferDao.countDone(partnerCertId, partnerServiceUuid)
        filesRemain.set(fileCount - filesDoneCount)
        return filesRemain.get()
    }

    init {
        // if the isolated process ends, we store everything downloaded so far and set state
        fileProcess.addIsolatedProcessListener {
            currentDBSet.forEach {
                if (it.value.state.notEqualsValue(TransferState.DONE)) {
                    transferDao.updateTransferredBytes(it.value.id.v(), it.value.transferred.v())
                    transferDao.updateState(it.value.id.v(), TransferState.SUSPENDED)
                }
            }
        }
        count()
    }

    override fun run() {
        try {
            while (!stopped) {
                if (count() > 0) {
                    // we will send this to the partner
                    val payload = FileTransferDetailsPayload()
                    currentDBSet = mutableMapOf()
                    val currentDetailSet = FileTransferDetailSet()
                    currentDetailSet.serviceUuid = driveService.uuid
                    payload.fileTransferDetailSet = currentDetailSet;
                    // prepare for retrieving the files and fill the payload so the partner can send us what we want
                    val dbTransfers: MutableList<DbTransferDetails> = transferDao.getNotStartedTransfers(partnerCertId, partnerServiceUuid, 66)
                    // countdown: when reaching zero we got to load a new batch
                    val batchCountDown = AtomicInteger(dbTransfers.size)
                    fun decreaseBatchCounter() {
                        val count = batchCountDown.decrementAndGet()
                        if (count == 0) {
                            waitLock.unlockWrite()
                            notification?.cancel()
                            notification = null
                        } else
                            showProgress()
                    }



                    if (dbTransfers.size > 0) {
                        dbTransfers.forEach { dbDetail ->
                            //update state first
                            dbDetail.state.v(TransferState.RUNNING)
                            transferDao.updateState(dbDetail.id.v(), dbDetail.state.v())


                            // find out where to store
                            val target = AFile.instance(driveService.getDriveSettings().getTransferDirectory(), dbDetail.hash.v())
                            val transferDetail = FileTransferDetail(target, Random.nextInt(), 0L, dbDetail.size.v())
                            transferDetail.hash = dbDetail.hash.v()

                            // clean up the mess after transfer
                            fun onComplete(ftd: FileTransferDetail) {
                                filesRemain.decrementAndGet()
                                // update states
                                dbDetail.state.v(TransferState.DONE)
                                transferDao.updateState(dbDetail.id.v(), dbDetail.state.v())
                                transferDao.updateTransferredBytes(dbDetail.id.v(), ftd.position)
                                // tell the sync handler we got a file
                                val transaction = T.lockingTransaction(fsDao)
                                try {
                                    driveService.syncHandler.onFileTransferred(target, dbDetail.hash.v(), transaction)
                                } finally {
                                    transaction.end()
                                }
                                decreaseBatchCounter()
                                //todo resume downloads
                                // check whether the received has the right hash
                            }

                            // check if complete
                            if (dbDetail.size.equalsValue(dbDetail.transferred)) {
                                onComplete(transferDetail)
                                return@forEach
                            }

                            transferDetail.setTransferDoneListener {
                                onComplete(it)
                            }
                            transferDetail.setTransferFailedListener {
                                filesRemain.decrementAndGet()
                                dbDetail.state.v(TransferState.SUSPENDED)
                                transferDao.updateState(dbDetail.id.v(), dbDetail.state.v())
                                decreaseBatchCounter()
                            }
                            transferDetail.setTransferProgressListener {
                                // store what we currently got
                                currentDBSet[it]?.transferred?.v(it.position)
                                transferDao.updateTransferredBytes(dbDetail.id.v(), it.position)
                                showProgress()
                                //todo resume downloads
                                // hash get the created hash objects and feed them with the new blocks
                            }
                            currentDetailSet.add(transferDetail)
                            currentDBSet.put(transferDetail, dbDetail)
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
                    }
//                    waitLock.lockWrite()
                } else {
                    Lok.debug("nothing more to do")
                    stopped = true
                    tManager.maintenance()
                }
                // todo check if any suspended transfers remain and try again
            }
        } catch (e: Exception) {
            Lok.error(e)
        } finally {
            Lok.debug("transfers from $partnerCertId/$partnerServiceUuid finished")
            tManager.onTransferFromServiceFinished(this)
        }
    }

    override fun getRunnableName(): String = "transferring from ${fileProcess.partnerCertificateId}/${fileProcess.partnerServiceUuid}"

    fun stop() {
        stopped = true
        waitLock.unlockWrite()
    }

}

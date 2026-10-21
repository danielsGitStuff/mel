package de.mel.filesync.transfer

import de.mel.Lok
import de.mel.MelRunnable
import de.mel.auth.MelNotification
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.IFile
import de.mel.auth.socket.process.transfer.FileTransferDetail
import de.mel.auth.socket.process.transfer.FileTransferDetailSet
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.service.MelFileSyncService
import de.mel.filesync.service.sync.SyncHandler
import de.mel.filesync.sql.DbTransferDetails
import de.mel.filesync.sql.TransferState
import de.mel.sql.Hash
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Retrieves files from one partner service.<br>
 * The files are asked for in batches. Each one is written to the transfer directory, under the name of its hash.<br>
 * What was received is written down in the database ([DbTransferDetails.transferred]) while it arrives. If the connection breaks, or the app dies,
 * a file that is half way there is not thrown away: the next attempt asks the partner for the rest, starting where the file on disk ends (see [resumePosition]).<br>
 * A file is only handed to the [SyncHandler] after its content was checked against its hash.
 */
class TransferFromServiceRunnable(val tManager: TManager, val fileProcess: MelIsolatedFileProcess) : MelRunnable {
    companion object {
        /**
         * How often a file may arrive with the wrong content before we give up (until the next time the partner connects).
         */
        const val MAX_ATTEMPTS_PER_FILE = 3

        /**
         * Files that are requested at once.
         */
        const val BATCH_SIZE = 66

        /**
         * How often we look whether the partner is still there while we wait for files
         */
        const val WATCH_INTERVAL_MS = 250L
    }

    /**
     * The files of the current batch and what we know about them. Concurrent: the connection and the batch itself both touch it.
     */
    private var currentDBSet: MutableMap<FileTransferDetail, DbTransferDetails> = ConcurrentHashMap()
    val fileSyncService: MelFileSyncService<out SyncHandler> = fileProcess.service as MelFileSyncService<out SyncHandler>
    private val transferDao = tManager.transferDao
    private val fsDao = tManager.fsDao
    private val partnerCertId = fileProcess.partnerCertificateId
    private val partnerServiceUuid = fileProcess.partnerServiceUuid
    @Volatile
    private var stopped = false
    private var notification: MelNotification? = null
    var filesRemain = AtomicLong(0L)

    /**
     * Guards the state of the transfers (database and [DbTransferDetails]): they are changed by the batch, the executor that checks files and the connection ending.
     */
    private val stateLock = Any()

    /**
     * hash -> how often the file arrived with wrong content
     */
    private val failedChecks = mutableMapOf<String, Int>()

    /**
     * The ids that tell the pieces of the files apart on the connection. Unique within this runnable.
     */
    private val streamIds = AtomicInteger(Random.nextInt())

    @Volatile
    private var batch: Batch? = null

    /**
     * When the partner last answered us or sent something. A partner that says nothing for too long is given up on, see [TManager.stallTimeoutMs].
     */
    private val lastActivity = AtomicLong(System.currentTimeMillis())

    private fun touch() {
        lastActivity.set(System.currentTimeMillis())
    }

    /**
     * The files that were asked for at the same time. It is over when all of them are either complete or given up on.
     */
    private class Batch {
        // one for the batch itself while it is put together
        private val outstanding = AtomicInteger(1)
        private val finished = CountDownLatch(1)

        fun add() {
            outstanding.incrementAndGet()
        }

        /**
         * @return the number of files that are still outstanding
         */
        fun oneLess(): Int {
            val left = outstanding.decrementAndGet()
            if (left == 0)
                finished.countDown()
            return left
        }

        /**
         * Do not wait for the rest.
         */
        fun release() {
            finished.countDown()
        }

        /**
         * @return whether the batch is over
         */
        fun await(timeoutMs: Long): Boolean = finished.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

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
                notification = MelNotification(fileSyncService.uuid, FileSyncStrings.Notifications.INTENTION_PROGRESS, title, text)
                fileSyncService.melAuthService.onNotificationFromService(fileSyncService, notification)
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


    private fun countRemaining(): Long {
        filesRemain.set(transferDao.countRemaining(partnerCertId, partnerServiceUuid))
        return filesRemain.get()
    }

    init {
        // if the isolated process ends, we store everything downloaded so far and set state
        fileProcess.addIsolatedProcessListener {
            suspendUnfinished()
            stop()
        }
        countRemaining()
    }

    /**
     * Where to continue a file: the part of it that is on disk and that the database knows to be complete, and is not more than the file is long.
     * If there is nothing to continue it starts at 0.<br>
     * The database is behind the disk when the app dies between writing and noting down, so the smaller of the two is the safe one.
     */
    private fun resumePosition(target: IFile, dbDetail: DbTransferDetails): Long {
        if (!target.exists())
            return 0L
        val size = dbDetail.size.v()
        val position = minOf(target.length(), dbDetail.transferred.v() ?: 0L)
        return if (position in 0..size) position else 0L
    }

    /**
     * @return whether the file is there with the size and the content it should have
     */
    private fun hasExpectedContent(target: IFile, dbDetail: DbTransferDetails): Boolean {
        if (!target.exists() || target.length() != dbDetail.size.v())
            return false
        val stream = target.inputStream() ?: return false
        return Hash.md5(stream) == dbDetail.hash.v()
    }

    private fun setState(dbDetail: DbTransferDetails, state: TransferState) {
        synchronized(stateLock) {
            dbDetail.state.v(state)
            transferDao.updateState(dbDetail.id.v(), state)
        }
    }

    private fun setTransferred(dbDetail: DbTransferDetails, transferred: Long) {
        synchronized(stateLock) {
            dbDetail.transferred.v(transferred)
            transferDao.updateTransferredBytes(dbDetail.id.v(), transferred)
        }
    }

    /**
     * Everything we have is on disk. If it is what we wanted the file is handed over, otherwise it is thrown away and has to come again.
     */
    private fun onFileComplete(dbDetail: DbTransferDetails, target: IFile) {
        if (hasExpectedContent(target, dbDetail)) {
            filesRemain.decrementAndGet()
            synchronized(stateLock) {
                dbDetail.state.v(TransferState.DONE)
                dbDetail.transferred.v(dbDetail.size.v())
                transferDao.updateState(dbDetail.id.v(), TransferState.DONE)
                transferDao.updateTransferredBytes(dbDetail.id.v(), dbDetail.size.v())
            }
            tManager.onFileTransferred(partnerCertId, partnerServiceUuid)
            // tell the sync handler we got a file
            val transaction = de.mel.auth.tools.lock2.P.confine(fsDao)
            try {
                fileSyncService.syncHandler.onFileTransferred(target, dbDetail.hash.v(), transaction)
            } finally {
                transaction.end()
            }
        } else {
            val hash = dbDetail.hash.v()
            Lok.error("$hash is not what we asked for (${target.length()} of ${dbDetail.size.v()} bytes), starting over")
            target.delete()
            synchronized(stateLock) {
                val failed = (failedChecks[hash] ?: 0) + 1
                failedChecks[hash] = failed
                dbDetail.transferred.v(0L)
                transferDao.updateTransferredBytes(dbDetail.id.v(), 0L)
                setState(dbDetail, if (failed < MAX_ATTEMPTS_PER_FILE) TransferState.NOT_STARTED else TransferState.SUSPENDED)
            }
        }
    }

    /**
     * Whatever of the current batch is not complete will not come on this connection. Remember how far it came so it can be continued.
     */
    private fun suspendUnfinished() {
        synchronized(stateLock) {
            currentDBSet.forEach {
                it.key.abort()
                if (it.value.state.equalsValue(TransferState.RUNNING)) {
                    transferDao.updateTransferredBytes(it.value.id.v(), it.value.transferred.v())
                    setState(it.value, TransferState.SUSPENDED)
                }
            }
        }
    }

    /**
     * Prepares the transfer of one file: decides where to start, opens the file and puts it in the request. Files that need no transfer are completed on the spot.
     *
     * @return whether it is part of the request
     */
    private fun prepare(dbDetail: DbTransferDetails, batch: Batch, requestedFiles: FileTransferDetailSet): Boolean {
        val target = AbstractFile.instance(fileSyncService.getFileSyncSettings().getTransferDirectory(), dbDetail.hash.v())
        val size = dbDetail.size.v()
        var start = resumePosition(target, dbDetail)
        setTransferred(dbDetail, start)
        setState(dbDetail, TransferState.RUNNING)

        if (start == size) {
            // all there. an empty file is not there yet.
            if (!target.exists())
                target.createNewFile()
            onFileComplete(dbDetail, target)
            return false
        }

        var transferDetail = FileTransferDetail(target, streamIds.incrementAndGet(), start, size)
        try {
            transferDetail.openWrite()
        } catch (e: Exception) {
            if (start == 0L) {
                Lok.error("cannot write ${target.getName()}: $e")
                setState(dbDetail, TransferState.SUSPENDED)
                return false
            }
            // this file cannot be continued. start over
            Lok.debug("cannot continue ${target.getName()} at $start, starting over")
            start = 0L
            target.delete()
            setTransferred(dbDetail, 0L)
            transferDetail = FileTransferDetail(target, streamIds.incrementAndGet(), start, size)
            try {
                transferDetail.openWrite()
            } catch (e: Exception) {
                Lok.error("cannot write ${target.getName()}: $e")
                setState(dbDetail, TransferState.SUSPENDED)
                return false
            }
        }
        transferDetail.hash = dbDetail.hash.v()

        transferDetail.setTransferDoneListener {
            touch()
            // the content is checked and handed over by another thread, the connection should go on
            val check = object : MelRunnable {
                override fun run() {
                    try {
                        onFileComplete(dbDetail, target)
                    } catch (e: Exception) {
                        Lok.error("could not complete ${dbDetail.hash.v()}: $e")
                        e.printStackTrace()
                        setState(dbDetail, TransferState.SUSPENDED)
                    } finally {
                        batch.oneLess()
                        showProgress()
                    }
                }

                override fun getRunnableName(): String = "checking ${dbDetail.hash.v()}"
            }
            try {
                fileSyncService.execute(check)
            } catch (e: RejectedExecutionException) {
                check.run()
            }
        }
        transferDetail.setTransferFailedListener {
            touch()
            filesRemain.decrementAndGet()
            setState(dbDetail, TransferState.SUSPENDED)
            batch.oneLess()
            showProgress()
        }
        transferDetail.setTransferProgressListener {
            touch()
            // store what we currently got
            setTransferred(dbDetail, it.position)
            showProgress()
        }
        batch.add()
        requestedFiles.add(transferDetail)
        currentDBSet[transferDetail] = dbDetail
        fileProcess.addFilesReceiving(transferDetail)
        return true
    }

    /**
     * Asks for the next files that are not retrieved yet, and waits until they are all complete or given up.
     *
     * @return whether it makes sense to go on
     */
    private fun retrieveBatch(dbTransfers: List<DbTransferDetails>): Boolean {
        val batch = Batch()
        this.batch = batch
        currentDBSet = ConcurrentHashMap()
        val payload = FileTransferDetailsPayload()
        val requestedFiles = FileTransferDetailSet()
        requestedFiles.serviceUuid = fileSyncService.uuid
        payload.fileTransferDetailSet = requestedFiles
        for (dbDetail in dbTransfers) {
            if (stopped)
                break
            prepare(dbDetail, batch, requestedFiles)
        }
        // everything is in place, from here on the batch is over when the last file is
        batch.oneLess()
        if (stopped || requestedFiles.details.isEmpty()) {
            // nothing to wait for. the files may have been completed on the spot
            suspendUnfinished()
            return !stopped
        }
        touch()
        val connected = fileSyncService.melAuthService.connect(partnerCertId)
        connected.onDone {
            try {
                payload.intent = FileSyncStrings.INTENT_PLEASE_TRANSFER
                // a request: if the partner cannot be reached or does not take it we hear about it, a message could get lost without anybody noticing
                it.request(partnerServiceUuid, payload).onDone {
                    touch()
                }.onError {
                    Lok.error("$partnerCertId did not take the request for files: $it")
                    suspendUnfinished()
                    batch.release()
                }
            } catch (e: Exception) {
                Lok.error("could not ask $partnerCertId for files: $e")
                suspendUnfinished()
                batch.release()
            }
        }.onError {
            suspendUnfinished()
            stopped = true
            batch.release()
        }
        while (!batch.await(WATCH_INTERVAL_MS)) {
            if (System.currentTimeMillis() - lastActivity.get() > tManager.stallTimeoutMs) {
                Lok.error("nothing came from $partnerCertId/$partnerServiceUuid for ${tManager.stallTimeoutMs} ms, giving up on this connection")
                fileProcess.stop()
                batch.release()
            }
        }
        // whatever is not complete now will not come on this connection
        suspendUnfinished()
        return !stopped
    }

    override fun run() {
        try {
            while (!stopped) {
                val dbTransfers: List<DbTransferDetails> = transferDao.getNotStartedTransfers(partnerCertId, partnerServiceUuid, BATCH_SIZE)
                if (dbTransfers.isEmpty()) {
                    Lok.debug("nothing more to do")
                    break
                }
                if (!retrieveBatch(dbTransfers))
                    break
            }
        } catch (e: Exception) {
            Lok.error(e)
        } finally {
            notification?.cancel()
            notification = null
            Lok.debug("transfers from $partnerCertId/$partnerServiceUuid finished")
            tManager.onTransferFromServiceFinished(this)
        }
    }

    override fun getRunnableName(): String = "transferring from ${fileProcess.partnerCertificateId}/${fileProcess.partnerServiceUuid}"

    fun stop() {
        stopped = true
        batch?.release()
    }

}

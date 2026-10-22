package de.mel.filesync.transfer

import de.mel.DeferredRunnable
import de.mel.Lok
import de.mel.auth.service.MelAuthService
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess
import de.mel.auth.socket.process.transfer.MelIsolatedProcess
import de.mel.auth.tools.N
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.nio.FileDistributionTask
import de.mel.filesync.service.MelFileSyncService
import de.mel.filesync.service.Wastebin
import de.mel.filesync.service.sync.SyncHandler
import de.mel.filesync.sql.DbTransferDetails
import de.mel.filesync.sql.TransferState
import de.mel.filesync.sql.dao.FsDao
import de.mel.filesync.sql.dao.TransferDao
import kotlinx.coroutines.runBlocking
import `fun`.with.promise.Promise
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Creates a TransferFromServiceRunnable per partner that you want to receive files from and manages them.<br>
 * A partner is a service of another instance. There is at most one [TransferFromServiceRunnable] per partner service at any time.
 */
class TManager(
    val melAuthService: MelAuthService,
    val transferDao: TransferDao,
    val melFileSyncService: MelFileSyncService<out SyncHandler>,
    val syncHandler: SyncHandler,
    val wastebin: Wastebin,
    val fsDao: FsDao
) : DeferredRunnable(), MelIsolatedProcess.IsolatedProcessListener {

    override fun onShutDown(): Promise<Void, Void> {
        stopRetrieving()
        wakeUp()
        return ResolvedDeferredObject()
    }

    /**
     * Guards [isolatedProcessesMap], [activeTFSRunnables] and [researchRequested]
     */
    private val lock = Object()

    /**
     * The isolated connections we know, by the certificate of the partner
     */
    val isolatedProcessesMap = mutableMapOf<Long, MelIsolatedFileProcess>()

    /**
     * The runnables that are retrieving files right now, by the connection they use
     */
    val activeTFSRunnables = mutableMapOf<MelIsolatedFileProcess, TransferFromServiceRunnable>()

    private var researchRequested = false

    init {
        maintenance()
    }

    /**
     * Something changed, dig the database for stuff we have to retrieve.
     * Connects to every service that has files that we want and feeds a [TransferFromServiceRunnable] for each.
     */
    fun research() {
        wakeUp()
        synchronized(lock) {
            researchRequested = true
            lock.notifyAll()
        }
    }

    private fun wakeUp() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    private fun awaitResearch() {
        synchronized(lock) {
            while (!researchRequested && !isStopped)
                lock.wait()
            researchRequested = false
        }
    }

    fun removeUnnecessaryTransfers() {
        //todo call this after first index
        Lok.debug("doing transfer housekeeping")
        // first delete things that are no longer required by FS
        N.sqlResource(transferDao.unnecessaryTransfers) { sqlResource ->
            runBlocking {
                var transfer = sqlResource.next
                while (transfer != null) {
                    transferDao.flagForDeletion(transfer.id.v(), true)
                    transfer = sqlResource.next
                }
            }
        }
        transferDao.deleteFlaggedForDeletion()


        // remove every file that has no transfer entry
        melFileSyncService.fileSyncSettings.transferDirectory.listFiles().forEach { file ->
            if (!transferDao.hasHash(file.getName()))
                file.delete()
        }
    }

    /**
     * do some housekeeping: delete transfers that are no longer required by FS and other useless files.
     * blocks!!! call this when booting!
     */
    fun maintenance() {
        removeUnnecessaryTransfers()
        // clean all copy jobs
        melFileSyncService.fileSyncDatabaseManager.fileDistTaskDao.deleteAll()
        // files that are half way there stay in the transfer directory, see TransferFromServiceRunnable.resumePosition
    }

    private val retryAttempts = ConcurrentHashMap<String, Int>()
    private val retryScheduled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * How long to wait before trying again to get files that could not be transferred. Doubles with every attempt that got nothing.
     */
    var retryBaseDelayMs = 2_000L
    var retryMaxDelayMs = 5 * 60_000L

    /**
     * A partner that says nothing for this long, while we wait for files, is given up on. The files continue on the next attempt, see [retryBaseDelayMs].
     */
    @Volatile
    var stallTimeoutMs = 120_000L

    /**
     * Gets the transfers from that service that were given up on going again, now. They continue where they stopped.
     */
    fun resumeSuspended(certId: Long, serviceUuid: String) {
        try {
            de.mel.auth.tools.lock2.P.confine(transferDao).run {
                transferDao.flagStateForRemainingTransfers(certId, serviceUuid, TransferState.NOT_STARTED)
            }.end()
            research()
        } catch (e: Exception) {
            Lok.error(e)
        }
    }

    private fun retryKey(certId: Long, serviceUuid: String) = "$certId/$serviceUuid"

    /**
     * A file arrived, so whatever was wrong is over.
     */
    fun onFileTransferred(certId: Long, serviceUuid: String) {
        retryAttempts.remove(retryKey(certId, serviceUuid))
    }

    /**
     * Transfers that were given up on (connection lost, partner gone...) are tried again after a while. Nobody else would: the partner does
     * not have to be spotted again for that.
     */
    private fun retryLater(certId: Long, serviceUuid: String) {
        if (isStopped)
            return
        val key = retryKey(certId, serviceUuid)
        if (!retryScheduled.add(key))
            return
        val attempt = retryAttempts.merge(key, 1, Int::plus) ?: 1
        val delay = minOf(retryMaxDelayMs, retryBaseDelayMs shl minOf(attempt - 1, 20))
        try {
            melAuthService.timeoutScheduler.schedule({
                retryScheduled.remove(key)
                if (!isStopped)
                    resumeSuspended(certId, serviceUuid)
            }, delay, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            // the instance is shutting down
            retryScheduled.remove(key)
        }
    }

    override fun onIsolatedProcessEnds(isolatedProcess: MelIsolatedProcess) {
        var runnable: TransferFromServiceRunnable? = null
        synchronized(lock) {
            // keep the house clean
            // several isolated connections to the same partner may exist: only forget this one
            if (isolatedProcessesMap[isolatedProcess.partnerCertificateId] === isolatedProcess)
                isolatedProcessesMap.remove(isolatedProcess.partnerCertificateId)
            runnable = activeTFSRunnables[isolatedProcess]
        }
        // also stop the retriever - if present. It tells us when it is done.
        runnable?.stop()
    }

    override fun getRunnableName(): String = "TransferManager for ${melAuthService.name}"

    private fun hasActiveRunnable(certId: Long, serviceUuid: String): Boolean = synchronized(lock) {
        activeTFSRunnables.keys.any { it.partnerCertificateId == certId && it.partnerServiceUuid == serviceUuid }
    }

    /**
     * Files that arrived in the transfer directory but were not placed yet, and files we have in the directory that were transferred as well.
     */
    private fun placeKnownFiles() {
        wastebin.restoreFsFiles()
        val fsTransaction = de.mel.auth.tools.lock2.P.confine(fsDao)
        try {
            fsDao.searchFsForTransfers().forEach { hash ->
                val fsFiles = fsDao.getSyncedFilesByHash(hash)
                if (fsFiles?.size!! > 0) {
                    val fsFile = fsFiles[0]
                    val aFile = fsDao.getFileByFsFile(melFileSyncService.fileSyncSettings.rootDirectory, fsFile)
                    // the syncHandler moves the file into its places if required.
                    // if not, the transfer entry is obsolete and can be deleted
                    if (!syncHandler.onFileTransferred(aFile, hash, fsTransaction, fsFile))
                        transferDao.flagForDeletionByHash(hash)
                }
            }
        } finally {
            fsTransaction.end()
        }
    }

    /**
     * Connects to the services that have files for us and sets a [TransferFromServiceRunnable] on each.
     * THIS METHOD BLOCKS until every connection is either established or failed.
     */
    private fun connectAndStart(dbTransferSets: List<DbTransferDetails>) {
        val connections = CountDownLatch(dbTransferSets.size)
        dbTransferSets.forEach { transferDetails ->
            val certId = transferDetails.certId.v()
            val serviceUuid = transferDetails.serviceUuid.v()
            try {
                melFileSyncService.getIsolatedProcess(MelIsolatedFileProcess::class.java, certId, serviceUuid)
                    .onDone { isolatedFileProcess ->
                        try {
                            synchronized(lock) {
                                isolatedProcessesMap[isolatedFileProcess.partnerCertificateId] = isolatedFileProcess
                            }
                            isolatedFileProcess.addIsolatedProcessListener(this)
                            startTransfersForIsoProcess(isolatedFileProcess)
                        } finally {
                            connections.countDown()
                        }
                    }.onError {
                        try {
                            Lok.error("cannot get files from $certId/$serviceUuid: $it")
                            de.mel.auth.tools.lock2.P.confine(transferDao).run {
                                transferDao.flagStateForRemainingTransfers(certId, serviceUuid, TransferState.SUSPENDED)
                            }.end()
                            retryLater(certId, serviceUuid)
                        } finally {
                            connections.countDown()
                        }
                    }
            } catch (e: Exception) {
                Lok.error(e)
                connections.countDown()
            }
        }
        connections.await(60, TimeUnit.SECONDS)
    }

    override fun runImpl() {
        val transferDirPath = melFileSyncService.fileSyncSettings.rootDirectory.path + File.separator + FileSyncStrings.TRANSFER_DIR
        val transferDir = File(transferDirPath)
        transferDir.mkdirs()

        while (!Thread.currentThread().isInterrupted && !isStopped) {
            try {
                // the partners that have files for us and that nobody retrieves them from yet
                val toServe = transferDao.twoTransferSets.filter { !hasActiveRunnable(it.certId.v(), it.serviceUuid.v()) }
                if (toServe.isEmpty()) {
                    Lok.debug("waiting until there is stuff to transfer...")
                    awaitResearch()
                    continue
                }
                placeKnownFiles()
                connectAndStart(toServe)
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Lok.error(e)
                // do not spin if this keeps going wrong
                Thread.sleep(1000)
            }
        }
        Lok.debug("TManager has done its job!")
    }

    private fun startTransfersForIsoProcess(isolatedProcess: MelIsolatedProcess) {
        val transfersRemain = transferDao.getNotStartedTransfers(isolatedProcess.partnerCertificateId, isolatedProcess.partnerServiceUuid, 1).isNotEmpty()
        if (transfersRemain) {
            retrieveFromService(isolatedProcess as MelIsolatedFileProcess)
        } else {
            Lok.debug("isolated process has nothing to transfer anymore")
        }
    }


    private fun retrieveFromService(fileProcess: MelIsolatedFileProcess) {
        val retrieveRunnable: TransferFromServiceRunnable
        synchronized(lock) {
            if (activeTFSRunnables.containsKey(fileProcess))
                return
            retrieveRunnable = TransferFromServiceRunnable(this, fileProcess)
            activeTFSRunnables[fileProcess] = retrieveRunnable
        }
        melFileSyncService.execute(retrieveRunnable)
    }

    fun createTransfer(detailsDb: DbTransferDetails) {
        detailsDb.state.v(TransferState.NOT_STARTED)
        transferDao.insert(detailsDb)
        // create a file distr job here
        val distributionTask = FileDistributionTask()
            .setSourceHash(detailsDb.hash.v())
            .setState(FileDistributionTask.FileDistributionState.NRY)
        syncHandler.fileDistributor.createJob(distributionTask)
    }

    /**
     * Ends everything that is going on: the retrievers and the connections they use.
     */
    private fun stopRetrieving() {
        val runnables: List<TransferFromServiceRunnable>
        val processes: List<MelIsolatedFileProcess>
        synchronized(lock) {
            runnables = activeTFSRunnables.values.toList()
            processes = (activeTFSRunnables.keys + isolatedProcessesMap.values).toList()
        }
        runnables.forEach { it.stop() }
        processes.forEach { it.stop() }
    }

    override fun stop() {
        super.stop()
        stopped = true
        stopRetrieving()
        wakeUp()
    }

    /**
     * Runs (again). What a stop cut off is taken up: a stop suspends the transfers that run, and their retry is not scheduled while stopped
     * (commitStage stops and starts this every time it applied a stage set, also while the partner fetches from us or we from it).
     */
    fun start() {
        synchronized(this) {
            N.r { de.mel.auth.tools.lock2.P.confine(transferDao).run { transferDao.resumeAllSuspended() }.end() }
            restart()
            melFileSyncService.execute(this)
            research()
        }
    }


    fun resume() {
        start()
    }

    fun onTransferFromServiceFinished(retrieveRunnable: TransferFromServiceRunnable) {
        val fileProcess = retrieveRunnable.fileProcess
        synchronized(lock) {
            activeTFSRunnables.remove(fileProcess, retrieveRunnable)
        }
        // this does not have to be precise cause it is for dev purposes only
        melFileSyncService.onTransfersDone()
        fileProcess.stop()
        if (transferDao.hasSuspended(fileProcess.partnerCertificateId, fileProcess.partnerServiceUuid))
            retryLater(fileProcess.partnerCertificateId, fileProcess.partnerServiceUuid)
        // whatever is left (or arrived meanwhile) is looked at
        research()
    }

}

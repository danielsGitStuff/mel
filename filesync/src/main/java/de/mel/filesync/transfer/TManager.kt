package de.mel.filesync.transfer

import de.mel.DeferredRunnable
import de.mel.Lok
import de.mel.auth.service.MelAuthService
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess
import de.mel.auth.socket.process.transfer.MelIsolatedProcess
import de.mel.auth.tools.CountLock
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
import org.jdeferred.Promise
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Creates a TransferFromServiceRunnable per partner that you want to receive files from and manages them.
 */
class TManager(val melAuthService: MelAuthService, val transferDao: TransferDao, val melFileSyncService: MelFileSyncService<out SyncHandler>
               , val syncHandler: SyncHandler, val wastebin: Wastebin, val fsDao: FsDao) : DeferredRunnable(), MelIsolatedProcess.IsolatedProcessListener {

    override fun onShutDown(): Promise<Void, Void, Void> = ResolvedDeferredObject()

    companion object {
        private val FILE_REQUEST_LIMIT_PER_CONNECTION: Int = 30

    }

    val isolatedProcessesMap = mutableMapOf<Long, MelIsolatedFileProcess>()
    val activeTFSRunnables = mutableMapOf<MelIsolatedFileProcess, TransferFromServiceRunnable>()

    val waitLock = CountLock()

    init {
        if (melAuthService.name=="MAClient")
            Lok.debug("debug tm")
        maintenance()
    }

    /**
     * dig the database for stuff we have to retrieve.
     * connect to every drive service that has files that we want,
     * then feed the MelIsolatedFileProcesses
     */
    fun research() {
        waitLock.unlock()
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
        // todo resume downloads
        // open hash objects here and check whether the size on disk matches the files size
    }


    override fun onIsolatedProcessEnds(isolatedProcess: MelIsolatedProcess) {
        val transaction = de.mel.auth.tools.lock2.P.confine(isolatedProcessesMap, activeTFSRunnables)
        try {
            // keep the house clean
            isolatedProcessesMap.remove(isolatedProcess.partnerCertificateId)
            // also stop the retriever - if present
            activeTFSRunnables.remove(isolatedProcess)?.stop()
        } finally {
            transaction.end()
        }
    }

    override fun getRunnableName(): String = "TransferManager for ${melAuthService.name}"


    /**
     * dig the database for stuff we have to retrieve.
     * connect to every drive service that has files that we want,
     * then feed the MelIsolatedFileProcesses.
     * THIS METHOD BLOCKS!
     */
    private fun connect2RemainingInstances(dbTransferSets: MutableList<DbTransferDetails>) {
        val transaction = de.mel.auth.tools.lock2.P.confine(isolatedProcessesMap)
        val atomicInt = AtomicInteger(dbTransferSets.size)
        fun checkAtom() {
            // check if all connections hve been established or failed.
            if (atomicInt.decrementAndGet() == 0) {
                transaction.end();
                waitLock.unlock()
            }
        }
        if (atomicInt.get() > 0) {
            val transferSets = transferDao.twoTransferSets
            if (transferSets.size > 0) {
                transferSets.forEach { transferDetails ->
                    val isolatedPromise = melFileSyncService.getIsolatedProcess(MelIsolatedFileProcess::class.java, transferDetails.certId.v(), transferDetails.serviceUuid.v())
                    isolatedPromise.done { isolatedFileProcess ->
                        isolatedProcessesMap[isolatedFileProcess.partnerCertificateId] = isolatedFileProcess
                        checkAtom()
                        isolatedFileProcess.addIsolatedProcessListener(this)
                        startTransfersForIsoProcess(isolatedFileProcess)
                    }.fail {
                        val c = transferDetails
                        de.mel.auth.tools.lock2.P.confine(transferDao).run {
                            transferDao.flagStateForRemainingTransfers(transferDetails.certId.v(), transferDetails.serviceUuid.v(), TransferState.SUSPENDED)
                        }.end()
                        checkAtom()
                    }
                }
            } else {
                transaction.end()
            }
        } else {
            transaction.end()
        }
    }


    override fun runImpl() {
        val transferDirPath = melFileSyncService.fileSyncSettings.rootDirectory.path + File.separator + FileSyncStrings.TRANSFER_DIR
        val transferDir = File(transferDirPath)
        transferDir.mkdirs()

        while (!Thread.currentThread().isInterrupted && !isStopped) {
            val groupedTransferSets = transferDao.twoTransferSets
            if (groupedTransferSets.size == 0) {
                Lok.debug("waiting until there is stuff to transfer...")
                waitLock.lock()
            } else {

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
//                                transferDao.deleteByHash(hash)
                        }
                    }
                } finally {
                    fsTransaction.end()
                }
                connect2RemainingInstances(groupedTransferSets)
                val isoTransaction = de.mel.auth.tools.lock2.P.confine(isolatedProcessesMap)
                try {
                    val values = mutableListOf<MelIsolatedFileProcess>()
                    values.addAll(isolatedProcessesMap.values)
                    values.forEach { fileProcess ->
                        startTransfersForIsoProcess(fileProcess)
                    }
                } finally {
                    isoTransaction.end()
                }
            }
        }
        Lok.debug("TManager has done its job!")
    }

    private fun startTransfersForIsoProcess(isolatedProcess: MelIsolatedProcess) {
        val transfersRemain = transferDao.hasNotStartedTransfers(isolatedProcess.partnerCertificateId, isolatedProcess.partnerServiceUuid)
        if (transfersRemain) {
            retrieveFromService(isolatedProcess as MelIsolatedFileProcess)
        } else {
            Lok.debug("isolated process has nothing to transfer anymore")
        }
    }


    private fun retrieveFromService(fileProcess: MelIsolatedFileProcess) {
        val transaction = de.mel.auth.tools.lock2.P.confine(activeTFSRunnables)
        try {
            if (!activeTFSRunnables.containsKey(fileProcess)) {
                val retrieveRunnable = TransferFromServiceRunnable(this, fileProcess)
                activeTFSRunnables[fileProcess] = retrieveRunnable
                melFileSyncService.execute(retrieveRunnable)
            }
        } finally {
            transaction.end()
        }
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

    override fun stop() {
        de.mel.auth.tools.lock2.P.confine(this).run {
            super.stop()
            stopped = true
            N.r {
                activeTFSRunnables.forEach {
                    it.value.stop()
                    it.key.stop()
                }
                isolatedProcessesMap.values.forEach { it.stop() }
            }
        }.end()
    }

    fun start() {
        de.mel.auth.tools.lock2.P.confine(this).run {
            stopped = false
            melFileSyncService.execute(this)
            waitLock.unlock()
        }.end()
    }


    fun resume() {
        start()
    }

    fun onTransferFromServiceFinished(retrieveRunnable: TransferFromServiceRunnable) {
        val transaction = de.mel.auth.tools.lock2.P.confine(activeTFSRunnables)
        try {
            with(retrieveRunnable.fileProcess) {
                activeTFSRunnables.remove(this)
//                transferDao.deleteDone(partnerCertificateId, partnerServiceUuid)
//                val count = transferDao.count(partnerCertificateId, partnerServiceUuid)
//                val done = transferDao.countRemaining(partnerCertificateId, partnerServiceUuid)
//                if (count == done)
                // this does not have to be precise cause it is for dev purposes only
                melFileSyncService.onTransfersDone()
            }
        } finally {
            transaction.end()
        }
        retrieveRunnable.fileProcess.stop()
    }

}
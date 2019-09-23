package de.mein.drive.transfer

import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.auth.service.MeinAuthService
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess
import de.mein.auth.tools.CountLock
import de.mein.auth.tools.CountWaitLock
import de.mein.auth.tools.N
import de.mein.auth.tools.lock.T
import de.mein.drive.data.DriveStrings
import de.mein.drive.nio.FileDistributionTask
import de.mein.drive.service.MeinDriveService
import de.mein.drive.service.Wastebin
import de.mein.drive.service.sync.SyncHandler
import de.mein.drive.sql.DbTransferDetails
import de.mein.drive.sql.TransferState
import de.mein.drive.sql.dao.FsDao
import de.mein.drive.sql.dao.TransferDao
import de.mein.sql.RWLock
import kotlinx.coroutines.runBlocking
import org.jdeferred.Promise
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Creates a TransferFromServiceRunnable per partner that you want to receive files from and manages them.
 */
class TManager(val meinAuthService: MeinAuthService, val transferDao: TransferDao, val meinDriveService: MeinDriveService<out SyncHandler>
               , val syncHandler: SyncHandler, val wastebin: Wastebin, val fsDao: FsDao) : DeferredRunnable(), MeinIsolatedProcess.IsolatedProcessListener {

    override fun onShutDown(): Promise<Void, Void, Void> = ResolvedDeferredObject()

    companion object {
        private val FILE_REQUEST_LIMIT_PER_CONNECTION: Int = 30

    }

    val isolatedProcessesMap = mutableMapOf<Long, MeinIsolatedFileProcess>()
    val activeTFSRunnables = mutableMapOf<MeinIsolatedFileProcess, TransferFromServiceRunnable>()

    val waitLock = CountLock()

    init {
        maintenance()
    }

    /**
     * dig the database for stuff we have to retrieve.
     * connect to every drive service that has files that we want,
     * then feed the MeinIsolatedFileProcesses
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
        meinDriveService.driveSettings.transferDirectory.listFiles().forEach { file ->
            if (!transferDao.hasHash(file.name))
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
        meinDriveService.driveDatabaseManager.fileDistTaskDao.deleteAll()
        // todo resume downloads
        // open hash objects here and check whether the size on disk matches the files size
    }


    override fun onIsolatedProcessEnds(isolatedProcess: MeinIsolatedProcess) {
        val transaction = T.lockingTransaction(isolatedProcessesMap, activeTFSRunnables)
        try {
            // keep the house clean
            isolatedProcessesMap.remove(isolatedProcess.partnerCertificateId)
            // also stop the retriever - if present
            activeTFSRunnables.remove(isolatedProcess)?.stop()
        } finally {
            transaction.end()
        }
    }

    override fun getRunnableName(): String = "TransferManager for ${meinAuthService.name}"


    /**
     * dig the database for stuff we have to retrieve.
     * connect to every drive service that has files that we want,
     * then feed the MeinIsolatedFileProcesses.
     * THIS METHOD BLOCKS!
     */
    private fun connect2RemainingInstances(dbTransferSets: MutableList<DbTransferDetails>) {
        val transaction = T.lockingTransaction(isolatedProcessesMap)
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
                    val isolatedPromise = meinDriveService.getIsolatedProcess(MeinIsolatedFileProcess::class.java, transferDetails.certId.v(), transferDetails.serviceUuid.v())
                    isolatedPromise.done { isolatedFileProcess ->
                        isolatedProcessesMap[isolatedFileProcess.partnerCertificateId] = isolatedFileProcess
                        checkAtom()
                        isolatedFileProcess.addIsolatedProcessListener(this)
                        startTransfersForIsoProcess(isolatedFileProcess)
                    }.fail {
                        val c = transferDetails
                        T.lockingTransaction(transferDao).run {
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
        val transferDirPath = meinDriveService.driveSettings.rootDirectory.path + File.separator + DriveStrings.TRANSFER_DIR
        val transferDir = File(transferDirPath)
        transferDir.mkdirs()

        while (!Thread.currentThread().isInterrupted && !isStopped) {
            val groupedTransferSets = transferDao.twoTransferSets
            if (groupedTransferSets.size == 0) {
                Lok.debug("waiting until there is stuff to transfer...")
                waitLock.lock()
            } else {

                wastebin.restoreFsFiles()
                val fsTransaction = T.lockingTransaction(fsDao)
                try {
                    fsDao.searchTransfer().forEach { hash ->
                        val fsFiles = fsDao.getSyncedFilesByHash(hash)
                        if (fsFiles?.size!! > 0) {
                            val fsFile = fsFiles[0]
                            val aFile = fsDao.getFileByFsFile(meinDriveService.driveSettings.rootDirectory, fsFile)
                            // the syncHandler moves the file into its places if equired.
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
                val isoTransaction = T.lockingTransaction(isolatedProcessesMap)
                try {
                    val values = mutableListOf<MeinIsolatedFileProcess>()
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

    private fun startTransfersForIsoProcess(isolatedProcess: MeinIsolatedProcess) {
        val transfersRemain = transferDao.hasNotStartedTransfers(isolatedProcess.partnerCertificateId, isolatedProcess.partnerServiceUuid)
        if (transfersRemain) {
            retrieveFromService(isolatedProcess as MeinIsolatedFileProcess)
        } else {
            Lok.debug("isolated process has nothing to transfer anymore")
        }
    }


    private fun retrieveFromService(fileProcess: MeinIsolatedFileProcess) {
        val transaction = T.lockingTransaction(activeTFSRunnables)
        try {
            if (!activeTFSRunnables.containsKey(fileProcess)) {
                val retrieveRunnable = TransferFromServiceRunnable(this, fileProcess)
                activeTFSRunnables[fileProcess] = retrieveRunnable
                meinDriveService.execute(retrieveRunnable)
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
        T.lockingTransaction(this).run {
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
        T.lockingTransaction(this).run {
            stopped = false
            meinDriveService.execute(this)
            waitLock.unlock()
        }.end()
    }


    fun resume() {
        start()
    }

    fun onTransferFromServiceFinished(retrieveRunnable: TransferFromServiceRunnable) {
        val transaction = T.lockingTransaction(activeTFSRunnables)
        try {
            with(retrieveRunnable.fileProcess) {
                activeTFSRunnables.remove(this)
//                transferDao.deleteDone(partnerCertificateId, partnerServiceUuid)
//                val count = transferDao.count(partnerCertificateId, partnerServiceUuid)
//                val done = transferDao.countRemaining(partnerCertificateId, partnerServiceUuid)
//                if (count == done)
                // this does not have to be precise cause it is for dev purposes only
                meinDriveService.onTransfersDone()
            }
        } finally {
            transaction.end()
        }
        retrieveRunnable.fileProcess.stop()
    }

}
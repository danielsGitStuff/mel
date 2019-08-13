package de.mein.drive.transfer

import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.auth.service.MeinAuthService
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess
import de.mein.auth.tools.N
import de.mein.auth.tools.lock.T
import de.mein.drive.data.DriveStrings
import de.mein.drive.service.MeinDriveService
import de.mein.drive.service.Wastebin
import de.mein.drive.service.sync.SyncHandler
import de.mein.drive.sql.DbTransferDetails
import de.mein.drive.sql.TransferState
import de.mein.drive.sql.dao.FsDao
import de.mein.drive.sql.dao.TransferDao
import de.mein.sql.RWLock
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class TManager(val meinAuthService: MeinAuthService, val transferDao: TransferDao, val meinDriveService: MeinDriveService<out SyncHandler>
               , val syncHandler: SyncHandler, val wastebin: Wastebin, val fsDao: FsDao) : DeferredRunnable(), MeinIsolatedProcess.IsolatedProcessListener {
    companion object {
        private val FILE_REQUEST_LIMIT_PER_CONNECTION: Int = 30

    }

    val isolatedProcessesMap = mutableMapOf<Long, MeinIsolatedFileProcess>()
    val activeTFSRunnables = mutableMapOf<MeinIsolatedFileProcess, TransferFromServiceRunnable>()

    val waitLock = RWLock()

    init {
        maintenance()
    }

    /**
     * dig the database for stuff we have to retrieve.
     * connect to every drive service that has files that we want,
     * then feed the MeinIsolatedFileProcesses
     */
    fun research() {
        waitLock.unlockWrite()
    }

    /**
     * do some housekeeping: delete transfers that are no longer required by FS and other useless files.
     * blocks!!! call this when booting!
     */
    fun maintenance() {
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
        // clean all copy jobs
        meinDriveService.driveDatabaseManager.fileDistTaskDao.deleteAll()

        // remove every file that has no transfer entry
        meinDriveService.driveSettings.transferDirectory.listFiles().forEach { file ->
            if (!transferDao.hasHash(file.name))
                file.delete()
        }
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

    override fun onShutDown() {

    }

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
                waitLock.unlockWrite()
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
                waitLock.lockWrite()
            } else {

                wastebin.restoreFsFiles()
                val fsTransaction = T.lockingTransaction(fsDao)
                try {
                    fsDao.searchTransfer().forEach { hash ->
                        val fsFiles = fsDao.getSyncedFilesByHash(hash)
                        if (fsFiles?.size!! > 0) {
                            val fsFile = fsFiles[0]
                            val aFile = fsDao.getFileByFsFile(meinDriveService.driveSettings.rootDirectory, fsFile)
                            syncHandler.onFileTransferred(aFile, hash, fsTransaction, fsFile)
                            transferDao.updateStateByHash(hash, TransferState.DONE)
//                            transferDao.deleteByHash(hash)
                        }
                    }
                } finally {
                    fsTransaction.end()
                }
                connect2RemainingInstances(groupedTransferSets)
                val isoTransaction = T.lockingTransaction(isolatedProcessesMap)
                try {
                    isolatedProcessesMap.values.forEach { fileProcess ->
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
    }

    override fun stop() {
        super.stop()
        stopped = true
        activeTFSRunnables.forEach {
            it.value.stop()
            it.key.stop()
        }
        isolatedProcessesMap.values.forEach { it.stop() }
    }

    fun start() {
        meinDriveService.execute(this)
        waitLock.unlockWrite()
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
//                val done = transferDao.countDone(partnerCertificateId, partnerServiceUuid)
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
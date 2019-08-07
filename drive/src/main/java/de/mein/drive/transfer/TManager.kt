package de.mein.drive.transfer

import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.auth.service.MeinAuthService
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess
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

    }

    /**
     * dig the database for stuff we have to retrieve.
     * connect to every drive service that has files that we want,
     * then feed the MeinIsolatedFileProcesses
     */
    fun research() {
        waitLock.unlockWrite()
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
                        val transfersRemain = transferDao.hasNotStartedTransfers(fileProcess.partnerCertificateId, fileProcess.partnerServiceUuid)
                        if (transfersRemain) {
                            retrieveFromService(fileProcess)
                        } else {
                            Lok.debug("isolated process has nothing to transfer anymore")
                        }
                    }
                } finally {
                    isoTransaction.end()
                }
            }
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
    }

    fun start() {
        meinDriveService.execute(this)
        waitLock.unlockWrite()
    }


    fun resume() {

    }

    fun onTransferFromServiceFinished(retrieveRunnable: TransferFromServiceRunnable) {
        val transaction = T.lockingTransaction(activeTFSRunnables)
        try {
            with(retrieveRunnable.fileProcess) {
                activeTFSRunnables.remove(this)
                transferDao.removeDone(partnerCertificateId, partnerServiceUuid)
                if (transferDao.count(partnerCertificateId, partnerServiceUuid) == 0L)
                    meinDriveService.onTransfersDone()
            }
        } finally {
            transaction.end()
        }
        retrieveRunnable.fileProcess.stop()
    }

}
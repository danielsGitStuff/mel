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
import de.mein.drive.sql.TransferDetails
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
        val transaction = T.lockingTransaction(isolatedProcessesMap)
        isolatedProcessesMap.remove(isolatedProcess.partnerCertificateId)
        transaction.end()

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
    private fun connect2RemainingInstances(transferSets: MutableList<TransferDetails>) {
        val transaction = T.lockingTransaction(isolatedProcessesMap)
        val atomicInt = AtomicInteger(transferSets.size)
        fun checkAtom() {
            // check if all connections hve been established or failed.
            if (atomicInt.decrementAndGet() == 0) {
                transaction.end();
                waitLock.unlockWrite()
            }
        }
        if (atomicInt.get() > 0) {
            transferDao.twoTransferSets.forEach {
                val isolatedPromise = meinDriveService.getIsolatedProcess(MeinIsolatedFileProcess::class.java, it.certId.v(), it.serviceUuid.v())
                isolatedPromise.done { isolatedFileProcess ->
                    isolatedProcessesMap[isolatedFileProcess.partnerCertificateId] = isolatedFileProcess
                    isolatedFileProcess.addIsolatedProcessListener(this)
                }.always { _, _, _ -> checkAtom() }
            }
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

                wastebin.restoreFsFiles(syncHandler)
                val fsTransaction = T.lockingTransaction(fsDao)
                try {
                    fsDao.searchTransfer().forEach { hash ->
                        val fsFiles = fsDao.getFilesByHash(hash)
                        if (fsFiles?.size!! > 0) {
                            val fsFile = fsFiles[0]
                            val aFile = fsDao.getFileByFsFile(meinDriveService.driveSettings.rootDirectory, fsFile)
                            syncHandler.onFileTransferred(aFile, hash, fsTransaction)
                            transferDao.deleteByHash(hash)
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

    fun createTransfer(details: TransferDetails) {
        details.state.v(TransferState.NOT_STARTED)
        transferDao.insert(details)
    }

    fun start() {
        meinDriveService.execute(this)
        waitLock.unlockWrite()
    }


    fun resume() {

    }

}
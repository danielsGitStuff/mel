package de.mein.drive.nio

import de.mein.auth.file.AFile
import de.mein.auth.jobs.Job
import de.mein.auth.service.MeinWorker
import de.mein.auth.tools.lock.T
import de.mein.auth.tools.lock.Transaction
import de.mein.drive.bash.BashTools
import de.mein.drive.service.sync.SyncHandler
import de.mein.drive.sql.dao.FsDao
import de.mein.drive.sql.dao.TransferDao
import java.io.File
import java.util.*

class FileDistributorImplPC : FileDistributorImpl, MeinWorker() {
    override fun moveBlocking(source: AFile<*>, target: AFile<*>, fsId: Long?, fsDao: FsDao?) {
        if (target.exists())
            return
        val s = File(source.absolutePath)
        val t = File(target.absolutePath)
        val moved = s.renameTo(t)
        if (moved) {
            fsDao?.setSynced(fsId, true)
        }
    }

    override fun workWork(job: Job<*, *, *>?) {
        val distributionTask = (job as FileJob).distributionTask
        // lock file so it won't change from now on
        val input = distributionTask.sourceFile.inputStream()
        if (distributionTask.hasOptionals()) {
            // abort if optional details do not match the current source file
            val stored = distributionTask.sourceDetails
            val read = BashTools.getFsBashDetails(distributionTask.sourceFile)
            if (read.isSymLink != stored.isSymLink
                    || read.modified != stored.modified
                    || read.getiNode() != stored.getiNode()
                    || distributionTask.sourceFile.length() != distributionTask.size)
                return
        }
        // stack because...
        val targetStack = Stack<AFile<*>>()
        targetStack.addAll(distributionTask.targetFiles)
        val targetIds = Stack<Long>()
        targetIds.addAll(distributionTask.targetFsIds)

        val sourceFile = File(distributionTask.sourceFile.absolutePath)
        // ...the last file is arbitrary
        val lastFile = targetStack.pop()
        val lastId = targetIds.pop()
        // ... and the rest works like this
        while (!targetStack.empty()) {
            copyFile(sourceFile, targetStack.pop(), targetIds.pop())
        }
        if (distributionTask.deleteSource) {
            // move file
            if (lastFile.exists())
                return
            sourceFile.renameTo(File(lastFile.absolutePath))
            // update synced flag
            T.lockingTransaction(fsDao)
                    .run { fsDao.setSynced(lastId, true) }
                    .end()
            // delete from transfer
            T.lockingTransaction(transferDao)
                    .run {
                        transferDao.deleteByHash(distributionTask.sourceHash)
                    }
        } else {
            copyFile(sourceFile, lastFile, lastId)
        }
    }

    private fun copyFile(source: File, target: AFile<*>, fsId: Long) {
        val input = source.inputStream()
        if (target.exists())
            return
        var transaction: Transaction<*>? = null
        try {
            val out = target.outputStream()
            try {
                // Transfer bytes from in to out
                val buf = ByteArray(1024)

                var len = 0
                do {
                    len = input.read(buf)
                    if (len > 0)
                        out.write(buf, 0, len)
                } while (len > 0)
            } finally {
                out.close()
            }
            // update details
            transaction = T.lockingTransaction(fsDao)
            val fsBashDetails = BashTools.getFsBashDetails(target)
            val fsTarget = fsDao.getFile(fsId)
            fsTarget.getiNode().v(fsBashDetails.getiNode())
            fsTarget.modified.v(fsBashDetails.modified)
            fsTarget.size.v(target.length())
            fsTarget.synced.v(true)
            fsDao.update(fsTarget)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            input.close()
            transaction?.end()
        }
    }

    lateinit var syncHandler: SyncHandler
    lateinit var fsDao: FsDao
    lateinit var transferDao: TransferDao
    override fun init(syncHandler: SyncHandler) {
        this.syncHandler = syncHandler
        this.fsDao = syncHandler.fsDao
        this.transferDao = syncHandler.transferDao
    }


    override fun workOnTask(fileJob: FileJob) {
        addJob(fileJob)
    }


    override fun getRunnableName(): String = "FileDistributor"


}
package de.mein.drive.nio

import de.mein.auth.file.AFile
import de.mein.auth.tools.lock.Transaction
import de.mein.drive.service.sync.SyncHandler
import de.mein.drive.sql.dao.FsDao

class FileDistributor(syncHandler: SyncHandler) {
    val instance: FileDistributorImpl

    init {
        instance = FILE_DISTR_CLASS!!.newInstance()
        instance.init(syncHandler)
    }

    fun addJob(fileJob: FileJob) {
        instance.workOnTask(fileJob)
    }

    /**
     * moves source file to target file and sets the target-fs-entry to synced (IF PROVIDED).
     */
    fun moveBlocking(source: AFile<*>, target: AFile<*>, fsId: Long?, fsDao: FsDao?) {
        instance.moveBlocking(source, target, fsId, fsDao)
    }

    companion object {
        private var FILE_DISTR_CLASS: Class<out FileDistributorImpl>? = FileDistributorImplPC::class.java
        fun setFileDistributorImpl(fileDistributorClass: Class<out FileDistributorImpl>) {
            FILE_DISTR_CLASS = fileDistributorClass
        }
    }
}
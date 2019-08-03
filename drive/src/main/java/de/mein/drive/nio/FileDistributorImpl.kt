package de.mein.drive.nio

import de.mein.auth.file.AFile
import de.mein.auth.tools.lock.Transaction
import de.mein.drive.service.sync.SyncHandler
import de.mein.drive.sql.dao.FsDao

interface FileDistributorImpl {
    fun init(fileDistributorImplImpl: SyncHandler)

    fun workOnTask(fileJob: FileJob)
    fun moveBlocking(source: AFile<*>, target: AFile<*>, fsId: Long?, fsDao: FsDao?)
}
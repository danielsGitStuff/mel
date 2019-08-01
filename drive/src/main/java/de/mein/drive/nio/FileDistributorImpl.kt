package de.mein.drive.nio

import de.mein.auth.file.AFile
import de.mein.drive.service.sync.SyncHandler

interface FileDistributorImpl {
    fun init(fileDistributorImplImpl: SyncHandler)

    fun workOnTask(fileJob: FileJob)
    fun moveBlocking(source: AFile<*>, target: AFile<*>, fsId: Long?)
}
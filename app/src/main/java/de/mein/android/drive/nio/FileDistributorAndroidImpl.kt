package de.mein.android.drive.nio

import de.mein.auth.file.AFile
import de.mein.drive.nio.FileDistributorImpl
import de.mein.drive.nio.FileJob
import de.mein.drive.service.sync.SyncHandler

class FileDistributorAndroidImpl : FileDistributorImpl {
    private lateinit var syncHandler: SyncHandler

    override fun init(syncHandler: SyncHandler) {
        this.syncHandler = syncHandler;
    }

    override fun workOnTask(fileJob: FileJob) {
    }

    override fun moveBlocking(source: AFile<*>, target: AFile<*>, fsId: Long?) {
    }
}
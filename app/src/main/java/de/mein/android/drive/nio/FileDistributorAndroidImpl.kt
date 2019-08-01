package de.mein.android.drive.nio

import android.content.Intent
import de.mein.android.Tools
import de.mein.android.service.CopyService
import de.mein.auth.file.AFile
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer
import de.mein.drive.nio.FileDistributorImpl
import de.mein.drive.nio.FileJob
import de.mein.drive.service.sync.SyncHandler

class FileDistributorAndroidImpl : FileDistributorImpl {
    private lateinit var syncHandler: SyncHandler

    override fun init(syncHandler: SyncHandler) {
        this.syncHandler = syncHandler;
    }

    override fun workOnTask(fileJob: FileJob) {
        val serviceIntent = Intent(Tools.getApplicationContext(), FileDistributorService::class.java)
        val json = SerializableEntitySerializer.serialize(fileJob.distributionTask)
        serviceIntent.putExtra(FileDistributorService.TASK, json)
        Tools.getApplicationContext().startService(serviceIntent)
    }


    override fun moveBlocking(source: AFile<*>, target: AFile<*>, fsId: Long?) {
    }
}
package de.mel.dump

import de.mel.drive.FileSyncBootloader
import de.mel.drive.data.FileSyncSettings
import de.mel.drive.service.MelFileSyncService
import java.io.File

open class DumpBootloader : FileSyncBootloader() {
    override fun createInstance(fileSyncSettings: FileSyncSettings, workingDirectory: File, serviceTypeId: Long, uuid: String): MelFileSyncService<*> {
        val dumpService = if (fileSyncSettings.isServer) TargetService(melAuthService, workingDirectory, serviceTypeId!!, uuid!!, fileSyncSettings) else SourceService(melAuthService, workingDirectory, serviceTypeId!!, uuid!!, fileSyncSettings)
        return dumpService
    }

    override fun getName(): String = "File Dump"

    override fun getDescription(): String = "One way file backup"
}
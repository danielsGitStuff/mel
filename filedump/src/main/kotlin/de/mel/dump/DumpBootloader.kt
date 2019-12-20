package de.mel.dump

import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.filesync.FileSyncBootloader
import de.mel.filesync.data.FileSyncSettings
import de.mel.filesync.service.MelFileSyncService
import de.mel.filesync.sql.FileSyncDatabaseManager
import java.io.File

open class DumpBootloader : FileSyncBootloader() {

    override fun createInstance(fileSyncSettings: FileSyncSettings, workingDirectory: File, serviceTypeId: Long, uuid: String, databaseManager: FileSyncDatabaseManager): MelFileSyncService<*> {
        val dumpService = if (fileSyncSettings.isServer) TargetService(melAuthService, workingDirectory, serviceTypeId!!, uuid!!, fileSyncSettings, databaseManager) else SourceService(melAuthService, workingDirectory, serviceTypeId!!, uuid!!, fileSyncSettings, databaseManager)
        return dumpService
    }

    override fun isCompatiblePartner(service: ServiceJoinServiceType): Boolean {
        return service.type.equalsValue(DumpStrings.TYPE) && service.additionalServicePayload != null;
    }

    override fun getName(): String = "File Dump"

    override fun getDescription(): String = "One way file backup"
}
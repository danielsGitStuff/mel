package de.mel.dump

import de.mel.auth.service.MelAuthServiceImpl
import de.mel.filesync.data.FileSyncSettings
import de.mel.filesync.service.MelFileSyncClientService
import de.mel.filesync.service.sync.ClientSyncHandler
import de.mel.filesync.sql.FileSyncDatabaseManager
import java.io.File

class SourceService(melAuthService: MelAuthServiceImpl, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, dumpSettings: FileSyncSettings, databaseManager: FileSyncDatabaseManager) : MelFileSyncClientService(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, dumpSettings, databaseManager) {
    override fun initSyncHandler(): ClientSyncHandler = SourceSyncHandler(melAuthService, this)
}

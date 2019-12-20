package de.mel.dump

import de.mel.auth.service.MelAuthServiceImpl
import de.mel.filesync.data.FileSyncSettings
import de.mel.filesync.service.MelFileSyncServerService
import de.mel.filesync.service.sync.ServerSyncHandler
import de.mel.filesync.sql.FileSyncDatabaseManager
import java.io.File

class TargetService(melAuthService: MelAuthServiceImpl, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, fileSyncSettings: FileSyncSettings, databaseManager: FileSyncDatabaseManager) : MelFileSyncServerService(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, fileSyncSettings, databaseManager) {

    override fun initSyncHandler(): ServerSyncHandler = TargetSyncHandler(melAuthService, this)


}
package de.mel.dump

import de.mel.auth.service.MelAuthService
import de.mel.drive.data.DriveSettings
import de.mel.drive.service.MelDriveServerService
import de.mel.drive.service.sync.ServerSyncHandler
import java.io.File

class TargetService(melAuthService: MelAuthService, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, driveSettings: DriveSettings) : MelDriveServerService(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, driveSettings) {

    override fun initSyncHandler(): ServerSyncHandler = TargetSyncHandler(melAuthService, this)


}
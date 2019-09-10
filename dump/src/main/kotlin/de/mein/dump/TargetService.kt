package de.mein.dump

import de.mein.auth.service.MeinAuthService
import de.mein.drive.data.DriveSettings
import de.mein.drive.service.MeinDriveServerService
import de.mein.drive.service.sync.ServerSyncHandler
import java.io.File

class TargetService(meinAuthService: MeinAuthService, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, driveSettings: DriveSettings) : MeinDriveServerService(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, driveSettings) {

    override fun initSyncHandler(): ServerSyncHandler = TargetSyncHandler(meinAuthService, this)


}
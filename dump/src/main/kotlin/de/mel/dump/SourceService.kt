package de.mel.dump

import de.mel.auth.service.MelAuthService
import de.mel.drive.data.DriveSettings
import de.mel.drive.service.MelDriveClientService
import java.io.File

class SourceService(melAuthService: MelAuthService, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, dumpSettings: DriveSettings) : MelDriveClientService(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, dumpSettings) {
}
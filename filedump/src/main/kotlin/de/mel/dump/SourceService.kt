package de.mel.dump

import de.mel.auth.service.MelAuthService
import de.mel.drive.data.FileSyncSettings
import de.mel.drive.service.MelFileSyncClientService
import java.io.File

class SourceService(melAuthService: MelAuthService, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, dumpSettings: FileSyncSettings) : MelFileSyncClientService(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, dumpSettings) {
}
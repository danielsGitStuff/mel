package de.mel.dump

import de.mel.auth.service.MelAuthService
import de.mel.filesync.data.FileSyncSettings
import de.mel.filesync.service.MelFileSyncClientService
import java.io.File

class SourceService(melAuthService: MelAuthService, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, dumpSettings: FileSyncSettings) : MelFileSyncClientService(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, dumpSettings) {
}
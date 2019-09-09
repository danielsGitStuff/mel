package de.mein.dump

import de.mein.auth.service.MeinAuthService
import de.mein.drive.service.MeinDriveClientService
import java.io.File

class SourceService(meinAuthService: MeinAuthService, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, dumpSettings: DumpSettings) : MeinDriveClientService(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, dumpSettings) {
}
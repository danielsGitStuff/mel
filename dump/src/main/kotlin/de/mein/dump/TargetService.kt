package de.mein.dump

import de.mein.auth.data.ServicePayload
import de.mein.auth.data.db.Certificate
import de.mein.auth.service.MeinAuthService
import de.mein.auth.socket.process.`val`.Request
import de.mein.drive.service.MeinDriveServerService
import java.io.File

class TargetService(meinAuthService: MeinAuthService, serviceInstanceWorkingDirectory: File, serviceTypeId: Long, uuid: String, dumpSettings: DumpSettings) : MeinDriveServerService(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, dumpSettings) {
    init {
        syncHandler = TargetSyncHandler(meinAuthService, this)
    }
}
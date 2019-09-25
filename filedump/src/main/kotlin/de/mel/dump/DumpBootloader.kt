package de.mel.dump

import de.mel.DeferredRunnable
import de.mel.Lok
import de.mel.auth.MelNotification
import de.mel.auth.data.db.Service
import de.mel.auth.service.BootException
import de.mel.auth.service.Bootloader
import de.mel.auth.service.MelAuthService
import de.mel.auth.service.MelService
import de.mel.auth.socket.MelValidationProcess
import de.mel.auth.tools.CountdownLock
import de.mel.auth.tools.N
import de.mel.core.serialize.exceptions.JsonDeserializationException
import de.mel.core.serialize.exceptions.JsonSerializationException
import de.mel.drive.DriveBootloader
import de.mel.drive.DriveBootloader.DEV_DriveBootListener
import de.mel.drive.data.DriveClientSettingsDetails
import de.mel.drive.data.DriveSettings
import de.mel.drive.data.DriveStrings
import de.mel.drive.data.DriveStrings.Notifications
import de.mel.drive.service.MelDriveService
import de.mel.drive.sql.DriveDatabaseManager
import de.mel.sql.SqlQueriesException
import org.jdeferred.Promise
import org.jdeferred.impl.DeferredObject
import java.io.File
import java.io.IOException
import java.sql.SQLException

open class DumpBootloader : DriveBootloader() {
    override fun createInstance(driveSettings: DriveSettings, workingDirectory: File, serviceTypeId: Long, uuid: String): MelDriveService<*> {
        val dumpService = if (driveSettings.isServer) TargetService(melAuthService, workingDirectory, serviceTypeId!!, uuid!!, driveSettings) else SourceService(melAuthService, workingDirectory, serviceTypeId!!, uuid!!, driveSettings)
        return dumpService
    }

    override fun getName(): String = "File Dump"

    override fun getDescription(): String = "One way file backup"
}
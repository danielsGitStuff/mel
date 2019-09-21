package de.mein.dump

import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.auth.MeinNotification
import de.mein.auth.data.db.Service
import de.mein.auth.service.BootException
import de.mein.auth.service.Bootloader
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinService
import de.mein.auth.socket.MeinValidationProcess
import de.mein.auth.tools.CountdownLock
import de.mein.auth.tools.N
import de.mein.core.serialize.exceptions.JsonDeserializationException
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.drive.DriveBootloader
import de.mein.drive.DriveBootloader.DEV_DriveBootListener
import de.mein.drive.data.DriveClientSettingsDetails
import de.mein.drive.data.DriveSettings
import de.mein.drive.data.DriveStrings
import de.mein.drive.data.DriveStrings.Notifications
import de.mein.drive.service.MeinDriveService
import de.mein.drive.sql.DriveDatabaseManager
import de.mein.sql.SqlQueriesException
import org.jdeferred.Promise
import org.jdeferred.impl.DeferredObject
import java.io.File
import java.io.IOException
import java.sql.SQLException

open class DumpBootloader : DriveBootloader() {
    override fun createInstance(driveSettings: DriveSettings, workingDirectory: File, serviceTypeId: Long, uuid: String): MeinDriveService<*> {
        val dumpService = if (driveSettings.isServer) TargetService(meinAuthService, workingDirectory, serviceTypeId!!, uuid!!, driveSettings) else SourceService(meinAuthService, workingDirectory, serviceTypeId!!, uuid!!, driveSettings)
        return dumpService
    }

    override fun getName(): String = "File Dump"

    override fun getDescription(): String = "One way file backup"
}
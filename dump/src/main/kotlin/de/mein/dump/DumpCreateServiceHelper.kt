package de.mein.dump

import de.mein.auth.data.db.Service
import de.mein.auth.data.db.ServiceType
import de.mein.auth.file.AFile
import de.mein.auth.service.MeinAuthService
import de.mein.core.serialize.exceptions.JsonDeserializationException
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.drive.DriveCreateServiceHelper
import de.mein.drive.data.DriveSettings
import de.mein.drive.data.DriveStrings
import de.mein.drive.data.fs.RootDirectory
import de.mein.sql.SqlQueriesException
import java.io.File
import java.io.IOException
import java.sql.SQLException

class DumpCreateServiceHelper(val meinAuthService: MeinAuthService) : DriveCreateServiceHelper(meinAuthService) {
    override fun createDbService(name: String?): Service {
        val type: ServiceType = meinAuthService.getDatabaseManager().getServiceTypeByName(DumpBootloader().name)
        return meinAuthService.databaseManager.createService(type.id.v(), name)
    }
}
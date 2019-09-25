package de.mel.dump

import de.mel.auth.data.db.Service
import de.mel.auth.data.db.ServiceType
import de.mel.auth.file.AFile
import de.mel.auth.service.MelAuthService
import de.mel.core.serialize.exceptions.JsonDeserializationException
import de.mel.core.serialize.exceptions.JsonSerializationException
import de.mel.drive.DriveCreateServiceHelper
import de.mel.drive.data.DriveSettings
import de.mel.drive.data.DriveStrings
import de.mel.drive.data.fs.RootDirectory
import de.mel.sql.SqlQueriesException
import java.io.File
import java.io.IOException
import java.sql.SQLException

class DumpCreateServiceHelper(val melAuthService: MelAuthService) : DriveCreateServiceHelper(melAuthService) {
    override fun createDbService(name: String?): Service {
        val type: ServiceType = melAuthService.getDatabaseManager().getServiceTypeByName(DumpBootloader().name)
        return melAuthService.databaseManager.createService(type.id.v(), name)
    }
}
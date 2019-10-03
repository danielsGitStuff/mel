package de.mel.dump

import de.mel.auth.data.db.Service
import de.mel.auth.data.db.ServiceType
import de.mel.auth.service.MelAuthService
import de.mel.filesync.FileSyncCreateServiceHelper

class DumpCreateServiceHelper(val melAuthService: MelAuthService) : FileSyncCreateServiceHelper(melAuthService) {
    override fun createDbService(name: String?): Service {
        val type: ServiceType = melAuthService.getDatabaseManager().getServiceTypeByName(DumpBootloader().name)
        return melAuthService.databaseManager.createService(type.id.v(), name)
    }
}
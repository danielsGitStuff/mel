package de.mel.location

import de.mel.auth.data.db.Service
import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.service.BootException
import de.mel.auth.service.Bootloader
import de.mel.auth.service.MelAuthService
import de.mel.auth.tools.N
import de.mel.location.data.LocationSettings
import de.mel.location.data.LocationStrings
import de.mel.location.service.LocationService
import `fun`.with.promise.Promise
import java.io.File

open class LocationBootloader : Bootloader<LocationService>() {

    override fun getName(): String = LocationStrings.TYPE

    override fun getDescription(): String = "Location history & OwnTracks HTTP endpoint"

    override fun isCompatiblePartner(service: ServiceJoinServiceType): Boolean =
        service.type.equalsValue(LocationStrings.TYPE)

    override fun cleanUpDeletedService(melService: LocationService?, uuid: String?) {
        if (uuid != null) {
            File(bootLoaderDir, uuid).deleteRecursively()
        }
    }

    @Throws(BootException::class)
    fun createService(name: String, settings: LocationSettings): LocationService? {
        try {
            val type = melAuthService.databaseManager.getServiceTypeByName(this.name)
            val service = melAuthService.databaseManager.createService(type.id.v(), name)
            val serviceDir = File(bootLoaderDir, service.uuid.v())
            serviceDir.mkdirs()
            val jsonFile = File(serviceDir, LocationStrings.SETTINGS_FILENAME)
            settings.setJsonFile(jsonFile).save()
            melAuthService.melBoot.bootServices()
            return melAuthService.getMelService(service.uuid.v()) as? LocationService
        } catch (e: Exception) {
            throw BootException(this, e)
        }
    }

    override fun bootLevelShortImpl(melAuthService: MelAuthService, serviceDescription: Service): LocationService {
        val workingDir = melAuthService.melBoot.createServiceInstanceWorkingDir(serviceDescription)
        val settingsFile = File(workingDir, LocationStrings.SETTINGS_FILENAME)
        val settings = LocationSettings.load(settingsFile, null)
        val service = LocationService(
            melAuthService,
            workingDir,
            serviceDescription.typeId.v(),
            serviceDescription.uuid.v(),
            settings
        )
        melAuthService.execute(service)
        N.r { melAuthService.registerMelService(service) }
        return service
    }

    override fun bootLevelLongImpl(): Promise<Void, BootException>? = null
}

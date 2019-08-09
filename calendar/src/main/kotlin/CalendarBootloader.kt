import de.mein.Lok
import de.mein.auth.data.JsonSettings
import de.mein.auth.data.ServiceDetails
import de.mein.auth.data.db.Service
import de.mein.auth.service.Bootloader
import de.mein.auth.service.MeinAuthService
import de.mein.auth.tools.N
import de.mein.auth.tools.WaitLock
import java.io.File

class CalendarBootloader : Bootloader<CalendarService>() {
    override fun cleanUpDeletedService(meinService: CalendarService?, uuid: String?) {
        File(bootLoaderDir, uuid).delete()
    }

    override fun getName(): String = "calendar"

    override fun getDescription(): String = "syncs your calendars"

    override fun bootLevel1Impl(meinAuthService: MeinAuthService, service: Service): CalendarService {
        val jsonFile = File(bootLoaderDir.absolutePath + File.separator + service.uuid.v() + File.separator + CalendarStrings.SETTINGS_FILE_NAME)
        val calendarSettings: CalendarSettings<*> = JsonSettings.load(jsonFile) as CalendarSettings<*>
        return boot(meinAuthService, service, calendarSettings)
    }

    private fun boot(meinAuthService: MeinAuthService, service: Service, calendarSettings: CalendarSettings<*>): CalendarService {
        val workingDirectory = File(bootLoaderDir.getAbsolutePath(), service.uuid.v())
        val calendarService = if (calendarSettings.isServer()) {
            CalendarServerService(meinAuthService, workingDirectory, service.typeId.v(), service.uuid.v(), calendarSettings)
        } else {
            CalendarClientService(meinAuthService, workingDirectory, service.typeId.v(), service.uuid.v(), calendarSettings)
        }
        meinAuthService.registerMeinService(calendarService)
        meinAuthService.execute(calendarService)
        return calendarService
    }

    fun createService(name: String, calendarSettings: CalendarSettings<*>): CalendarService {
        val service: Service = createDbService(name)
        val serviceDir = File(bootLoaderDir.absolutePath + File.separator + service.uuid.v())
        serviceDir.mkdirs()
        val jsonFile = File(serviceDir, CalendarStrings.SETTINGS_FILE_NAME)
        calendarSettings.setJsonFile(jsonFile)
        val calendarService = boot(meinAuthService, service, calendarSettings)
        if (!calendarSettings.isServer()) {
            val waitLock = WaitLock().lock()
            val runner = N {
                meinAuthService.unregisterMeinService(service.uuid.v())
                meinAuthService.deleteService(service.uuid.v())
                Lok.debug("creating calendar service failed")
                waitLock.unlock()
            }
            val serverCertId: Long = calendarSettings.clientSettings!!.serverCertId!!
            val serverServiceUuid: String = calendarSettings.clientSettings!!.serviceUuid!!
            runner.runTry {
                meinAuthService.connect(serverCertId)
                        .done { mvp ->
                            runner.runTry {
                                mvp.request(serverServiceUuid, ServiceDetails(service.uuid.v(), CalendarStrings.INTENT_REG_AS_CLIENT))
                                        .done { waitLock.unlock() }
                                        .fail { runner.abort() }
                            }
                        }
                        .fail { runner.abort() }
                waitLock.lock()
            }
        }
        return calendarService
    }

    private fun createDbService(name: String): Service {
        val type = meinAuthService.databaseManager.getServiceTypeByName(this.name)
        return meinAuthService.databaseManager.createService(type.id.v(), name)
    }

}
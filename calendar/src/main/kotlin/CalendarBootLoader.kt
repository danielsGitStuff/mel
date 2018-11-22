import de.mein.auth.data.JsonSettings
import de.mein.auth.data.db.Service
import de.mein.auth.service.BootLoader
import de.mein.auth.service.MeinAuthService
import org.jdeferred.Promise
import java.io.File

class CalendarBootLoader : BootLoader() {
    override fun getName(): String = "calendar"

    override fun getDescription(): String = "syncs your calendars"

    override fun boot(meinAuthService: MeinAuthService, services: MutableList<Service>?): Promise<Void, Exception, Void>? {
        services?.forEach { service ->
            val jsonFile = File(bootLoaderDir.absolutePath + File.separator + service.uuid.v() + File.separator + CalendarStrings.SETTINGS_FILE_NAME)
            val calendarSettings: CalendarSettings<*> = JsonSettings.load(jsonFile) as CalendarSettings<*>
            boot(meinAuthService,service,calendarSettings);
        }
        return null
    }

    private fun boot(meinAuthService: MeinAuthService, service: Service, calendarSettings: CalendarSettings<*>) {

    }

    private fun createDbService(name: String, settings: CalendarSettings<*>): Service {
        val type = meinAuthService.databaseManager.getServiceTypeByName(this.name)
        return meinAuthService.databaseManager.createService(type.id.v(), name)
    }

}
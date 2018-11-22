import de.mein.auth.data.ClientData
import de.mein.auth.data.JsonSettings

interface PlatformCalendarSettings

class CalendarServerSettings : PlatformCalendarSettings {
    val clients = mutableSetOf<ClientData>()
}

class CalendarClientSettings : PlatformCalendarSettings {
    private var serverCertId: Long? = null
    private var serviceUuid: String? = null
    var role = ":("
}

class CalendarSettings<T : PlatformCalendarSettings> : JsonSettings() {
    var clientSettings: CalendarClientSettings? = null
    var serverSettings: CalendarServerSettings? = null
    var platformCalendarSettings: T? = null
    override fun init() {

    }

//    fun isServer(): Boolean {
//        return role == ContactStrings.ROLE_SERVER
//    }
}
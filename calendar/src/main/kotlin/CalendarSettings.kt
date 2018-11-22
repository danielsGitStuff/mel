import de.mein.auth.data.ClientData
import de.mein.auth.data.JsonSettings
import de.mein.core.serialize.SerializableEntity

interface PlatformCalendarSettings : SerializableEntity

class CalendarServerSettings : PlatformCalendarSettings {
    val clients = mutableSetOf<ClientData>()
}

class CalendarClientSettings : PlatformCalendarSettings {
    var serverCertId: Long? = null
    var serviceUuid: String? = null
}

class CalendarSettings<T : PlatformCalendarSettings> : JsonSettings() {
    var clientSettings: CalendarClientSettings? = null
    var serverSettings: CalendarServerSettings? = null
    var platformCalendarSettings: T? = null
    var role = ":("
    override fun init() {

    }

    fun isServer(): Boolean {
        return role == CalendarStrings.ROLE_SERVER
    }
}
package de.mel.location.data

import de.mel.auth.data.JsonSettings
import java.io.File

class LocationSettings : JsonSettings() {
    var port: Int = LocationStrings.DEFAULT_PORT
    var bindAddress: String = "0.0.0.0"
    var username: String = ""
    var password: String = ""

    override fun init() {}

    fun hasAuth(): Boolean = username.isNotBlank() || password.isNotBlank()

    companion object {
        @JvmStatic
        fun load(file: File, fallback: LocationSettings?): LocationSettings {
            val loaded = JsonSettings.load(file) as? LocationSettings
            if (loaded != null) {
                loaded.setJsonFile(file)
                return loaded
            }
            val cfg = fallback ?: LocationSettings()
            cfg.setJsonFile(file)
            cfg.save()
            return cfg
        }
    }
}

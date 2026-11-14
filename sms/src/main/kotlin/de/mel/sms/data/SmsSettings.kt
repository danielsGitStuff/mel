package de.mel.sms.data

import de.mel.auth.data.ClientData
import de.mel.auth.data.JsonSettings
import java.io.File

class SmsSettings : JsonSettings() {
    var role: String = SmsStrings.ROLE_CLIENT
    var secret: String? = null
    var shareSecretWithServer: Boolean = false
    var lastSyncedDateMs: Long = 0L
    var lastPulledServerId: Long = 0L
    var serverCertId: Long? = null
    var serverServiceUuid: String? = null
    var initFinished: Boolean = false
    var clients: MutableList<ClientData> = mutableListOf()

    override fun init() {}

    fun isServer(): Boolean = role == SmsStrings.ROLE_SERVER

    fun addClient(certId: Long, serviceUuid: String) {
        if (clients.none { it.certId == certId && it.serviceUuid == serviceUuid }) {
            clients.add(ClientData(certId, serviceUuid))
        }
    }

    companion object {
        @JvmStatic
        fun load(file: File, fallback: SmsSettings?): SmsSettings {
            val loaded = JsonSettings.load(file) as? SmsSettings
            if (loaded != null) {
                loaded.setJsonFile(file)
                return loaded
            }
            val cfg = fallback ?: SmsSettings()
            cfg.setJsonFile(file)
            cfg.save()
            return cfg
        }
    }
}

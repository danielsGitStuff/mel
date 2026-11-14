package de.mel.sms.service

import de.mel.Lok
import de.mel.auth.MelNotification
import de.mel.auth.data.db.Certificate
import de.mel.auth.service.Bootloader
import de.mel.auth.service.MelAuthService
import de.mel.auth.service.MelService
import de.mel.auth.socket.process.transfer.MelIsolatedProcess
import de.mel.auth.tools.N
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mel.sms.crypto.SmsCrypto
import de.mel.sms.data.SmsPlaintext
import de.mel.sms.data.SmsSettings
import de.mel.sms.data.db.SmsDatabaseManager
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

abstract class SmsService(
    melAuthService: MelAuthService,
    workingDirectory: File,
    serviceTypeId: Long,
    uuid: String,
    settingsCfg: SmsSettings?
) : MelService(melAuthService, workingDirectory, serviceTypeId, uuid, Bootloader.BootLevel.SHORT) {

    val databaseManager: SmsDatabaseManager = SmsDatabaseManager(workingDirectory, uuid, settingsCfg)
    val settings: SmsSettings = databaseManager.settings
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addOnChangedListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeOnChangedListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    protected fun notifySmsChanged() {
        listeners.forEach { N.r { it() } }
    }

    fun hasSecret(): Boolean = !settings.secret.isNullOrBlank()

    /** Decrypts all stored messages using [settings.secret]. Returns empty list if no secret is set. */
    fun decryptAllLocal(): List<SmsPlaintext> {
        val secret = settings.secret
        if (secret.isNullOrBlank()) return emptyList()
        val key = SmsCrypto.resolveKey(secret)
        val result = mutableListOf<SmsPlaintext>()
        for (rec in databaseManager.smsDao.loadAll()) {
            try {
                val json = SmsCrypto.decrypt(key, rec.iv, rec.ciphertext)
                val msg = SerializableEntityDeserializer.deserialize(json) as SmsPlaintext
                result.add(msg)
            } catch (e: Exception) {
                Lok.warn("Failed to decrypt SMS id=${rec.serverId}: ${e.message}")
            }
        }
        return result.sortedByDescending { it.dateMs }
    }

    override fun connectionAuthenticated(partnerCertificate: Certificate) {}
    override fun handleCertificateSpotted(partnerCertificate: Certificate) {}
    override fun onServiceRegistered() {}
    override fun createSendingNotification(): MelNotification? = null
    override fun onCommunicationsDisabled() {}
    override fun onCommunicationsEnabled() {}
    override fun onIsolatedConnectionEstablished(isolatedProcess: MelIsolatedProcess) {}
    override fun onBootLevel1Finished() {
        N.r { resolveStarted() }
    }
    override fun onBootLevel2Finished() {}
}

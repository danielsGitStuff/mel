package de.mel.sms

import de.mel.auth.data.ServiceDetails
import de.mel.auth.data.db.Service
import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.service.BootException
import de.mel.auth.service.Bootloader
import de.mel.auth.service.MelAuthService
import de.mel.auth.service.MelAuthServiceImpl
import de.mel.auth.tools.CountdownLock
import de.mel.auth.tools.N
import de.mel.sms.data.SmsSettings
import de.mel.sms.data.SmsStrings
import de.mel.sms.service.SmsClientService
import de.mel.sms.service.SmsServerService
import de.mel.sms.service.SmsService
import `fun`.with.promise.Promise
import java.io.File

open class SmsBootloader : Bootloader<SmsService>() {

    override fun getName(): String = SmsStrings.TYPE

    override fun getDescription(): String = "Encrypted SMS sync"

    override fun isCompatiblePartner(service: ServiceJoinServiceType): Boolean =
        service.type.equalsValue(SmsStrings.TYPE)

    override fun cleanUpDeletedService(melService: SmsService?, uuid: String?) {
        if (uuid != null) {
            File(bootLoaderDir, uuid).deleteRecursively()
        }
    }

    @Throws(BootException::class)
    fun createService(name: String, smsSettings: SmsSettings): SmsService? {
        try {
            val type = melAuthService.databaseManager.getServiceTypeByName(this.name)
            val service = melAuthService.databaseManager.createService(type.id.v(), name)
            val serviceDir = File(bootLoaderDir, service.uuid.v())
            serviceDir.mkdirs()
            val jsonFile = File(serviceDir, SmsStrings.SETTINGS_FILENAME)
            smsSettings.setJsonFile(jsonFile).save()
            melAuthService.melBoot.bootServices()
            return melAuthService.getMelService(service.uuid.v()) as? SmsService
        } catch (e: Exception) {
            throw BootException(this, e)
        }
    }

    override fun bootLevelShortImpl(melAuthService: MelAuthService, serviceDescription: Service): SmsService {
        val workingDir = melAuthService.melBoot.createServiceInstanceWorkingDir(serviceDescription)
        val settingsFile = File(workingDir, SmsStrings.SETTINGS_FILENAME)
        val settings = SmsSettings.load(settingsFile, null)

        val smsService: SmsService = if (settings.isServer()) {
            SmsServerService(melAuthService, workingDir, serviceDescription.typeId.v(), serviceDescription.uuid.v(), settings)
        } else {
            val client = SmsClientService(melAuthService, workingDir, serviceDescription.typeId.v(), serviceDescription.uuid.v(), settings)
            if (!client.settings.initFinished && client.settings.serverCertId != null && client.settings.serverServiceUuid != null) {
                registerWithServer(melAuthService as MelAuthServiceImpl, serviceDescription, client)
            }
            client
        }

        melAuthService.execute(smsService)
        N.r { melAuthService.registerMelService(smsService) }
        return smsService
    }

    override fun bootLevelLongImpl(): Promise<Void, BootException>? = null

    private fun registerWithServer(
        melAuthService: MelAuthServiceImpl,
        service: Service,
        client: SmsClientService
    ) {
        val serverCert = client.settings.serverCertId!!
        val serverServiceUuid = client.settings.serverServiceUuid!!
        val lock = CountdownLock(1)
        fun onFail() {
            client.shutDown()
            N.r {
                melAuthService.databaseManager.revoke(service.id.v(), serverCert)
                melAuthService.deleteService(service.uuid.v())
            }
            lock.unlock()
        }

        N.r { melAuthService.databaseManager.grant(service.id.v(), serverCert) }
        melAuthService.connect(serverCert).onDone { mvp ->
            N.r {
                val details = ServiceDetails(serverServiceUuid)
                details.intent = SmsStrings.INTENT_REG_AS_CLIENT
                mvp.request(serverServiceUuid, details)
                    .onDone {
                        client.settings.initFinished = true
                        client.settings.save()
                        if (client.settings.shareSecretWithServer && !client.settings.secret.isNullOrBlank()) {
                            client.shareSecretWithServerNow()
                        }
                        lock.unlock()
                    }
                    .onError { onFail() }
            }
        }.onError { onFail() }
        lock.lock()
    }
}

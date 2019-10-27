package de.mel.contacts

import de.mel.auth.service.Bootloader
import de.mel.auth.tools.N
import de.mel.contacts.data.ContactStrings
import de.mel.contacts.data.ContactsSettings
import de.mel.auth.data.ServiceDetails
import de.mel.contacts.service.ContactsClientService
import de.mel.contacts.service.ContactsServerService
import de.mel.contacts.service.ContactsService

import org.jdeferred.Promise

import java.io.File
import java.io.IOException
import java.sql.SQLException

import de.mel.auth.data.JsonSettings
import de.mel.auth.data.db.Service
import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.service.BootException
import de.mel.auth.service.MelAuthService
import de.mel.auth.tools.CountdownLock
import de.mel.core.serialize.exceptions.JsonDeserializationException
import de.mel.core.serialize.exceptions.JsonSerializationException
import de.mel.sql.SqlQueriesException

/**
 * Created by xor on 9/21/17.
 */

open class ContactsBootloader : Bootloader<ContactsService>() {
    override fun isCompatiblePartner(service: ServiceJoinServiceType): Boolean = service.type.equalsValue(ContactStrings.TYPE)

    override fun cleanUpDeletedService(melService: ContactsService?, uuid: String?) {
        File(bootLoaderDir, uuid).delete()
    }

    @Throws(BootException::class)
    fun createService(name: String, contactsSettings: ContactsSettings<*>): ContactsService? {
        var contactsService: ContactsService? = null
        val melBoot = melAuthService.melBoot
        try {
            val service = createDbService(name)
            val serviceDir = File(bootLoaderDir.absolutePath + File.separator + service.uuid.v())
            serviceDir.mkdirs()
            val jsonFile = File(serviceDir, "contacts.settings.json")
            contactsSettings.setJsonFile(jsonFile).save()
            melBoot.bootServices()
        } catch (e: IllegalAccessException) {
            throw BootException(this, e)
        } catch (e: JsonSerializationException) {
            throw BootException(this, e)
        } catch (e: IOException) {
            throw BootException(this, e)
        } catch (e: SqlQueriesException) {
            throw BootException(this, e)
        }

        return contactsService
    }

    @Throws(BootException::class)
    private fun boot(melAuthService: MelAuthService, service: Service, contactsSettings: ContactsSettings<*>?): ContactsService {
        val workingDirectory = melAuthService.melBoot.createServiceInstanceWorkingDir(service)
        var contactsService: ContactsService
        try {
            if (contactsSettings!!.isServer) {
                contactsService = createServerInstance(melAuthService, workingDirectory, service.typeId.v(), service.uuid.v(), contactsSettings)
            } else {
                contactsService = createClientInstance(melAuthService, workingDirectory, service.typeId.v(), service.uuid.v(), contactsSettings)
                val clientSettings = contactsSettings.clientSettings
                if (!clientSettings.initFinished) {
                    val serverCert = clientSettings.serverCertId
                    val serverServiceUuid = clientSettings.serviceUuid
                    val lock = CountdownLock(1)
                    fun onFail() {
                        contactsService.shutDown()
                        with(melAuthService.databaseManager) {
                            revoke(service.id.v(), serverCert)
                            melAuthService.deleteService(service.uuid.v())
                        }
                        lock.unlock()
                    }
                    //allow the server to communicate with us
                    N.r { melAuthService.databaseManager.grant(service.id.v(), serverCert) }
                    melAuthService.connect(serverCert)
                            .done { mvp ->
                                N.r {
                                    val serviceDetails = ServiceDetails(serverServiceUuid)
                                    serviceDetails.intent = ContactStrings.INTENT_REG_AS_CLIENT
                                    mvp.request(serverServiceUuid, serviceDetails)
                                            .done {
                                                clientSettings.initFinished = true
                                                contactsSettings.save()
                                                lock.unlock()
                                            }
                                            .fail { onFail() }
                                }
                            }.fail { onFail() }
                    lock.lock()
                }
            }
        } catch (e: Exception) {
            throw BootException(this, e)
        }

        melAuthService.execute(contactsService)
        val finalContactsService = contactsService
        N.r { melAuthService.registerMelService(finalContactsService) }
        return contactsService
    }

    @Throws(JsonDeserializationException::class, JsonSerializationException::class, IOException::class, SQLException::class, SqlQueriesException::class, IllegalAccessException::class, ClassNotFoundException::class)
    protected open fun createClientInstance(melAuthService: MelAuthService, workingDirectory: File, serviceTypeId: Long?, serviceUuid: String, settings: ContactsSettings<*>): ContactsService {
        return ContactsClientService(melAuthService, workingDirectory, serviceTypeId, serviceUuid, settings)
    }

    @Throws(JsonDeserializationException::class, JsonSerializationException::class, IOException::class, SQLException::class, SqlQueriesException::class, IllegalAccessException::class, ClassNotFoundException::class)
    protected open fun createServerInstance(melAuthService: MelAuthService, workingDirectory: File, serviceId: Long?, serviceTypeId: String, contactsSettings: ContactsSettings<*>): ContactsService {
        return ContactsServerService(melAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings)
    }

    @Throws(SqlQueriesException::class)
    private fun createDbService(name: String): Service {
        val type = melAuthService.databaseManager.getServiceTypeByName(ContactsBootloader().name)
        return melAuthService.databaseManager.createService(type.id.v(), name)
    }

    override fun getName(): String {
        return ContactStrings.TYPE
    }

    override fun getDescription(): String {
        return "synchronizes you contacts"
    }

    @Throws(BootException::class)
    override fun bootLevelShortImpl(melAuthService: MelAuthService, serviceDescription: Service): ContactsService {
        val instanceDir = melAuthService.melBoot.createServiceInstanceWorkingDir(serviceDescription)
        val jsonFile = File(instanceDir, ContactStrings.SETTINGS_FILE_NAME)
        var contactsSettings: ContactsSettings<*>? = null
        try {
            contactsSettings = JsonSettings.load(jsonFile) as ContactsSettings<*>
        } catch (e: IOException) {
            throw BootException(this, e)
        } catch (e: JsonDeserializationException) {
            throw BootException(this, e)
        } catch (e: JsonSerializationException) {
            throw BootException(this, e)
        } catch (e: IllegalAccessException) {
            throw BootException(this, e)
        }

        return boot(melAuthService, serviceDescription, contactsSettings)
    }

    @Throws(BootException::class)
    override fun bootLevelLongImpl(): Promise<Void, BootException, Void>? {
        return null
    }
}

package de.mein.contacts

import de.mein.Lok
import de.mein.auth.service.Bootloader
import de.mein.auth.tools.N
import de.mein.auth.tools.WaitLock
import de.mein.contacts.data.ContactStrings
import de.mein.contacts.data.ContactsClientSettings
import de.mein.contacts.data.ContactsSettings
import de.mein.auth.data.ServiceDetails
import de.mein.contacts.service.ContactsClientService
import de.mein.contacts.service.ContactsServerService
import de.mein.contacts.service.ContactsService

import org.jdeferred.Promise

import java.io.File
import java.io.IOException
import java.sql.SQLException

import de.mein.auth.data.JsonSettings
import de.mein.auth.data.db.Service
import de.mein.auth.data.db.ServiceType
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinBoot
import de.mein.auth.tools.CountdownLock
import de.mein.core.serialize.exceptions.JsonDeserializationException
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.sql.SqlQueriesException

/**
 * Created by xor on 9/21/17.
 */

open class ContactsBootloader : Bootloader<ContactsService>() {

    @Throws(Bootloader.BootException::class)
    fun createService(name: String, contactsSettings: ContactsSettings<*>): ContactsService? {
        var contactsService: ContactsService? = null
        val meinBoot = meinAuthService.meinBoot
        try {
            val service = createDbService(name)
            val serviceDir = File(bootLoaderDir.absolutePath + File.separator + service.uuid.v())
            serviceDir.mkdirs()
            val jsonFile = File(serviceDir, "contacts.settings.json")
            contactsSettings.setJsonFile(jsonFile).save()
            meinBoot.bootServices()

//            contactsService = boot(meinAuthService, service, contactsSettings)
//            if (!contactsSettings.isServer) {
//                // tell server we are here. if it goes wrong: reverse everything
//                val waitLock = WaitLock().lock()
//                val runner = N { e ->
//                    meinAuthService.unregisterMeinService(service.uuid.v())
//                    N.r { meinAuthService.databaseManager.deleteService(service.id.v()) }
//                    Lok.debug("ContactsBootloader.createDbService.service.deleted:something.failed")
//                    waitLock.unlock()
//                }
//                runner.runTry {
//                    //                    meinAuthService.connect(contactsSettings.clientSettings.serverCertId)
////                            .done { result ->
////                                val serverServiceUuid = contactsSettings.clientSettings.serviceUuid
////                                val serviceUuid = service.uuid.v()
////                                runner.runTry {
////                                    result.request(serverServiceUuid, ContactStrings.INTENT_REG_AS_CLIENT, ServiceDetails(serviceUuid))
////                                            .done { result1 -> waitLock.unlock() }.fail { result1 -> runner.abort() }
////
////                                }
////                            }.fail { result -> runner.abort() }
//                }
//                waitLock.lock()
//            }
        } catch (e: IllegalAccessException) {
            throw Bootloader.BootException(this, e)
        } catch (e: JsonSerializationException) {
            throw Bootloader.BootException(this, e)
        } catch (e: IOException) {
            throw Bootloader.BootException(this, e)
        } catch (e: SqlQueriesException) {
            throw Bootloader.BootException(this, e)
        }

        return contactsService
    }

    @Throws(Bootloader.BootException::class)
    private fun boot(meinAuthService: MeinAuthService, service: Service, contactsSettings: ContactsSettings<*>?): ContactsService {
        val workingDirectory = meinAuthService.meinBoot.createServiceInstanceWorkingDir(service)
        var contactsService: ContactsService
        try {
            if (contactsSettings!!.isServer) {
                contactsService = createServerInstance(meinAuthService, workingDirectory, service.typeId.v(), service.uuid.v(), contactsSettings)
            } else {
                contactsService = createClientInstance(meinAuthService, workingDirectory, service.typeId.v(), service.uuid.v(), contactsSettings)
                val clientSettings = contactsSettings.clientSettings
                if (!clientSettings.initFinished) {
                    val serverCert = clientSettings.serverCertId
                    val serverServiceUuid = clientSettings.serviceUuid
                    val lock = CountdownLock(1)
                    fun onFail() {
                        contactsService.shutDown()
                        with(meinAuthService.databaseManager) {
                            revoke(service.id.v(), serverCert)
                            deleteService(service.id.v())
                        }
                        lock.unlock()
                    }
                    //allow the server to communicate with us
                    N.r { meinAuthService.databaseManager.grant(service.id.v(), serverCert) }
                    meinAuthService.connect(serverCert)
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
            throw Bootloader.BootException(this, e)
        }

        meinAuthService.execute(contactsService)
        val finalContactsService = contactsService
        N.r { meinAuthService.registerMeinService(finalContactsService) }
        return contactsService
    }

    @Throws(JsonDeserializationException::class, JsonSerializationException::class, IOException::class, SQLException::class, SqlQueriesException::class, IllegalAccessException::class, ClassNotFoundException::class)
    protected open fun createClientInstance(meinAuthService: MeinAuthService, workingDirectory: File, serviceTypeId: Long?, serviceUuid: String, settings: ContactsSettings<*>): ContactsService {
        return ContactsClientService(meinAuthService, workingDirectory, serviceTypeId, serviceUuid, settings)
    }

    @Throws(JsonDeserializationException::class, JsonSerializationException::class, IOException::class, SQLException::class, SqlQueriesException::class, IllegalAccessException::class, ClassNotFoundException::class)
    protected open fun createServerInstance(meinAuthService: MeinAuthService, workingDirectory: File, serviceId: Long?, serviceTypeId: String, contactsSettings: ContactsSettings<*>): ContactsService {
        return ContactsServerService(meinAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings)
    }

    @Throws(SqlQueriesException::class)
    private fun createDbService(name: String): Service {
        val type = meinAuthService.databaseManager.getServiceTypeByName(ContactsBootloader().name)
        return meinAuthService.databaseManager.createService(type.id.v(), name)
    }

    override fun getName(): String {
        return ContactStrings.NAME
    }

    override fun getDescription(): String {
        return "synchronizes you contacts"
    }

    @Throws(Bootloader.BootException::class)
    override fun bootLevel1Impl(meinAuthService: MeinAuthService, serviceDescription: Service): ContactsService {
        val instanceDir = meinAuthService.meinBoot.createServiceInstanceWorkingDir(serviceDescription)
        val jsonFile = File(instanceDir, ContactStrings.SETTINGS_FILE_NAME)
        var contactsSettings: ContactsSettings<*>? = null
        try {
            contactsSettings = JsonSettings.load(jsonFile) as ContactsSettings<*>
        } catch (e: IOException) {
            throw Bootloader.BootException(this, e)
        } catch (e: JsonDeserializationException) {
            throw Bootloader.BootException(this, e)
        } catch (e: JsonSerializationException) {
            throw Bootloader.BootException(this, e)
        } catch (e: IllegalAccessException) {
            throw Bootloader.BootException(this, e)
        }

        return boot(meinAuthService, serviceDescription, contactsSettings)
    }

    @Throws(Bootloader.BootException::class)
    override fun bootLevel2Impl(): Promise<Void, Bootloader.BootException, Void>? {
        return null
    }
}

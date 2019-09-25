package de.mel.android.contacts

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.core.app.NotificationCompat
import android.view.ViewGroup

import java.io.File
import java.io.IOException
import java.sql.SQLException

import de.mel.Lok
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.MelActivity
import de.mel.android.Notifier
import de.mel.android.Threadder
import de.mel.android.boot.AndroidBootLoader
import de.mel.android.contacts.controller.AndroidContactsEditController
import de.mel.android.contacts.controller.RemoteContactsServiceChooserGuiController
import de.mel.android.contacts.data.AndroidContactSettings
import de.mel.android.contacts.service.AndroidContactsClientService
import de.mel.android.contacts.service.AndroidContactsServerService
import de.mel.android.controller.AndroidServiceGuiController
import de.mel.auth.MelNotification
import de.mel.auth.data.db.ServiceType
import de.mel.auth.service.IMelService
import de.mel.auth.service.MelAuthService
import de.mel.contacts.ContactsBootloader
import de.mel.contacts.data.ContactStrings
import de.mel.contacts.data.ContactsSettings
import de.mel.contacts.service.ContactsService
import de.mel.core.serialize.exceptions.JsonDeserializationException
import de.mel.core.serialize.exceptions.JsonSerializationException
import de.mel.sql.SqlQueriesException

/**
 * Created by xor on 9/21/17.
 */

class AndroidContactsBootloader : ContactsBootloader(), AndroidBootLoader<ContactsService> {

    @Throws(JsonDeserializationException::class, JsonSerializationException::class, IOException::class, SQLException::class, SqlQueriesException::class, IllegalAccessException::class, ClassNotFoundException::class)
    override fun createServerInstance(melAuthService: MelAuthService, workingDirectory: File, serviceId: Long?, serviceTypeId: String, contactsSettings: ContactsSettings<*>): ContactsService {
        return AndroidContactsServerService(melAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings)
    }


    @Throws(JsonDeserializationException::class, JsonSerializationException::class, IOException::class, SQLException::class, SqlQueriesException::class, IllegalAccessException::class, ClassNotFoundException::class)
    override fun createClientInstance(melAuthService: MelAuthService, workingDirectory: File, serviceTypeId: Long?, serviceUuid: String, settings: ContactsSettings<*>): ContactsService {
        return AndroidContactsClientService(melAuthService, workingDirectory, serviceTypeId, serviceUuid, settings)
    }

    override fun createService(activity: Activity, melAuthService: MelAuthService, currentController: AndroidServiceGuiController) {

        Lok.debug("")
        try {
            val controller = currentController as RemoteContactsServiceChooserGuiController
            val type = melAuthService.databaseManager.getServiceTypeByName(ContactsBootloader().name)
            val platformSettings = AndroidContactSettings().setPersistToPhoneBook(controller.persistToPhoneBook)
            val contactsSettings = ContactsSettings<AndroidContactSettings>()
            contactsSettings.role = controller.role
            if (contactsSettings.role == ContactStrings.ROLE_CLIENT) {
                contactsSettings.clientSettings.serverCertId = controller.selectedCertId
                contactsSettings.clientSettings.serviceUuid = controller.selectedService.uuid.v()
            }
            contactsSettings.setPlatformContactSettings(platformSettings)
            Threadder.runNoTryThread { createService(controller.name, contactsSettings) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    override fun inflateEmbeddedView(embedded: ViewGroup, activity: MainActivity, melAuthService: MelAuthService, runningInstance: IMelService?): AndroidServiceGuiController {
        return runningInstance?.let { AndroidContactsEditController(melAuthService, activity, it, embedded) }
                ?: RemoteContactsServiceChooserGuiController(melAuthService, activity, embedded)
    }

    override fun getPermissions(): Array<String> {
        return arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    }

    override fun getMenuIcon(): Int {
        return R.drawable.icon_notification_contacts_legacy
    }

    override fun createNotificationBuilder(context: Context, melService: IMelService, melNotification: MelNotification): NotificationCompat.Builder? {
        val intention = melNotification.intention
        return if (intention == ContactStrings.Notifications.INTENTION_CONFLICT) {
            NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SOUND)
        } else null
    }

    override fun createNotificationActivityClass(melService: IMelService, melNotification: MelNotification): Class<*>? {
        val intention = melNotification.intention
        return if (intention == ContactStrings.Notifications.INTENTION_CONFLICT) {
            ContactsConflictsPopupActivity::class.java
        } else null
    }
}

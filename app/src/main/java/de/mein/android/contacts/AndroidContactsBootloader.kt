package de.mein.android.contacts

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.core.app.NotificationCompat
import android.view.ViewGroup

import java.io.File
import java.io.IOException
import java.sql.SQLException

import de.mein.Lok
import de.mein.R
import de.mein.android.MainActivity
import de.mein.android.MeinActivity
import de.mein.android.Notifier
import de.mein.android.Threadder
import de.mein.android.boot.AndroidBootLoader
import de.mein.android.contacts.controller.AndroidContactsEditController
import de.mein.android.contacts.controller.RemoteContactsServiceChooserGuiController
import de.mein.android.contacts.data.AndroidContactSettings
import de.mein.android.contacts.service.AndroidContactsClientService
import de.mein.android.contacts.service.AndroidContactsServerService
import de.mein.android.controller.AndroidServiceGuiController
import de.mein.auth.MeinNotification
import de.mein.auth.data.db.ServiceType
import de.mein.auth.service.IMeinService
import de.mein.auth.service.MeinAuthService
import de.mein.contacts.ContactsBootloader
import de.mein.contacts.data.ContactStrings
import de.mein.contacts.data.ContactsSettings
import de.mein.contacts.service.ContactsService
import de.mein.core.serialize.exceptions.JsonDeserializationException
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.sql.SqlQueriesException

/**
 * Created by xor on 9/21/17.
 */

class AndroidContactsBootloader : ContactsBootloader(), AndroidBootLoader<ContactsService> {

    @Throws(JsonDeserializationException::class, JsonSerializationException::class, IOException::class, SQLException::class, SqlQueriesException::class, IllegalAccessException::class, ClassNotFoundException::class)
    override fun createServerInstance(meinAuthService: MeinAuthService, workingDirectory: File, serviceId: Long?, serviceTypeId: String, contactsSettings: ContactsSettings<*>): ContactsService {
        return AndroidContactsServerService(meinAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings)
    }


    @Throws(JsonDeserializationException::class, JsonSerializationException::class, IOException::class, SQLException::class, SqlQueriesException::class, IllegalAccessException::class, ClassNotFoundException::class)
    override fun createClientInstance(meinAuthService: MeinAuthService, workingDirectory: File, serviceTypeId: Long?, serviceUuid: String, settings: ContactsSettings<*>): ContactsService {
        return AndroidContactsClientService(meinAuthService, workingDirectory, serviceTypeId, serviceUuid, settings)
    }

    override fun createService(activity: Activity, meinAuthService: MeinAuthService, currentController: AndroidServiceGuiController) {

        Lok.debug("")
        try {
            val controller = currentController as RemoteContactsServiceChooserGuiController
            val type = meinAuthService.databaseManager.getServiceTypeByName(ContactsBootloader().name)
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

    override fun inflateEmbeddedView(embedded: ViewGroup, activity: MainActivity, meinAuthService: MeinAuthService, runningInstance: IMeinService?): AndroidServiceGuiController {
        return runningInstance?.let { AndroidContactsEditController(meinAuthService, activity, it, embedded) }
                ?: RemoteContactsServiceChooserGuiController(meinAuthService, activity, embedded)
    }

    override fun getPermissions(): Array<String> {
        return arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    }

    override fun getMenuIcon(): Int {
        return R.drawable.icon_notification_contacts_legacy
    }

    override fun createNotificationBuilder(context: Context, meinService: IMeinService, meinNotification: MeinNotification): NotificationCompat.Builder? {
        val intention = meinNotification.intention
        return if (intention == ContactStrings.Notifications.INTENTION_CONFLICT) {
            NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SOUND)
        } else null
    }

    override fun createNotificationActivityClass(meinService: IMeinService, meinNotification: MeinNotification): Class<*>? {
        val intention = meinNotification.intention
        return if (intention == ContactStrings.Notifications.INTENTION_CONFLICT) {
            ContactsConflictsPopupActivity::class.java
        } else null
    }
}

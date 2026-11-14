package de.mel.android.sms

import android.Manifest
import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import de.mel.AndroidPermission
import de.mel.Lok
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.Threadder
import de.mel.android.boot.AndroidBootLoader
import de.mel.android.controller.AndroidServiceGuiController
import de.mel.android.service.AndroidService
import de.mel.android.sms.controller.AndroidSmsCreateController
import de.mel.android.sms.controller.AndroidSmsEditController
import de.mel.auth.MelNotification
import de.mel.auth.data.db.Service
import de.mel.auth.service.IMelService
import de.mel.auth.service.MelAuthService
import de.mel.sms.SmsBootloader
import de.mel.sms.data.SmsSettings
import de.mel.sms.data.SmsStrings
import de.mel.sms.service.SmsClientService
import de.mel.sms.service.SmsService

class AndroidSmsBootloader : SmsBootloader(), AndroidBootLoader<SmsService> {

    private var smsObserver: ContentObserver? = null

    override fun bootLevelShortImpl(melAuthService: MelAuthService, serviceDescription: Service): SmsService {
        val svc = super.bootLevelShortImpl(melAuthService, serviceDescription)
        if (svc is SmsClientService) {
            val ctx = AndroidService.getInstance()
            if (ctx != null) {
                registerSmsObserver(ctx, svc)
                Threadder.runNoTryThread { AndroidSmsReader.syncNewSms(ctx, svc) }
            }
        }
        return svc
    }

    private fun registerSmsObserver(context: Context, client: SmsClientService) {
        if (!AndroidSmsReader.hasReadSmsPermission(context) || smsObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Threadder.runNoTryThread { AndroidSmsReader.syncNewSms(context, client) }
            }
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        smsObserver = observer
    }

    override fun createService(
        activity: Activity,
        melAuthService: MelAuthService,
        currentController: AndroidServiceGuiController
    ) {
        try {
            val ctrl = currentController as AndroidSmsCreateController
            val settings = SmsSettings()
            settings.role = if (ctrl.isServer) SmsStrings.ROLE_SERVER else SmsStrings.ROLE_CLIENT
            if (!settings.isServer()) {
                settings.serverCertId = ctrl.selectedCertId
                settings.serverServiceUuid = ctrl.selectedService.uuid.v()
                settings.shareSecretWithServer = ctrl.shareSecretWithServer
            }
            settings.secret = ctrl.secret?.takeIf { it.isNotBlank() }
            Threadder.runNoTryThread { createService(ctrl.name, settings) }
        } catch (e: Exception) {
            Lok.error("AndroidSmsBootloader.createService failed: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun inflateEmbeddedView(
        embedded: ViewGroup,
        activity: MainActivity,
        melAuthService: MelAuthService,
        runningInstance: IMelService?
    ): AndroidServiceGuiController {
        return if (runningInstance != null) {
            AndroidSmsEditController(melAuthService, activity, runningInstance, embedded)
        } else {
            AndroidSmsCreateController(melAuthService, activity, embedded, this)
        }
    }

    override fun getPermissions(): Array<AndroidPermission> = arrayOf(
        AndroidPermission(
            Manifest.permission.READ_SMS,
            titleInt = R.string.permissionRequiredTitle,
            textInt = R.string.permissionSms
        )
    )

    override fun getMenuIcon(): Int = R.drawable.icon_notification_contacts_legacy

    override fun createNotificationBuilder(
        context: Context,
        melService: IMelService,
        melNotification: MelNotification
    ): NotificationCompat.Builder? = null

    override fun createNotificationActivityClass(melService: IMelService, melNotification: MelNotification): Class<*>? = null
}

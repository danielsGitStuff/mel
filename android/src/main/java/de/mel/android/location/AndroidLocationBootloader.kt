package de.mel.android.location

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import de.mel.AndroidPermission
import de.mel.Lok
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.Threadder
import de.mel.android.boot.AndroidBootLoader
import de.mel.android.controller.AndroidServiceGuiController
import de.mel.android.location.controller.AndroidLocationCreateController
import de.mel.android.location.controller.AndroidLocationEditController
import de.mel.auth.MelNotification
import de.mel.auth.service.IMelService
import de.mel.auth.service.MelAuthService
import de.mel.location.LocationBootloader
import de.mel.location.service.LocationService

class AndroidLocationBootloader : LocationBootloader(), AndroidBootLoader<LocationService> {

    override fun createService(
        activity: Activity,
        melAuthService: MelAuthService,
        currentController: AndroidServiceGuiController
    ) {
        try {
            val ctrl = currentController as AndroidLocationCreateController
            val name = ctrl.serviceName
            val settings = ctrl.buildSettings()
            Threadder.runNoTryThread { createService(name, settings) }
        } catch (e: Exception) {
            Lok.error("AndroidLocationBootloader.createService failed: ${e.message}")
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
            AndroidLocationEditController(activity, runningInstance, embedded)
        } else {
            AndroidLocationCreateController(activity, embedded)
        }
    }

    override fun getPermissions(): Array<AndroidPermission> = emptyArray()

    override fun getMenuIcon(): Int = R.drawable.icon_notification_contacts_legacy

    override fun createNotificationBuilder(
        context: Context,
        melService: IMelService,
        melNotification: MelNotification
    ): NotificationCompat.Builder? = null

    override fun createNotificationActivityClass(melService: IMelService, melNotification: MelNotification): Class<*>? = null
}

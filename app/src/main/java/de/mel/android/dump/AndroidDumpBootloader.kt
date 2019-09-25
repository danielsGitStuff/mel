package de.mel.android.dump

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.boot.AndroidBootLoader
import de.mel.android.controller.AndroidServiceGuiController
import de.mel.android.drive.AndroidDriveBootloader
import de.mel.android.drive.controller.AndroidDriveEditGuiController
import de.mel.android.dump.controller.RemoteDumpServiceChooserGuiController
import de.mel.auth.MelNotification
import de.mel.auth.service.IMelService
import de.mel.auth.service.MelAuthService
import de.mel.drive.DriveCreateServiceHelper
import de.mel.drive.data.DriveStrings
import de.mel.drive.service.MelDriveService
import de.mel.dump.DumpBootloader
import de.mel.dump.DumpCreateServiceHelper

class AndroidDumpBootloader : DumpBootloader(), AndroidBootLoader<MelDriveService<*>> {

    private val driveBootLoader = object : AndroidDriveBootloader() {
        override fun createCreateServiceHelper(melAuthService: MelAuthService): DriveCreateServiceHelper = DumpCreateServiceHelper(melAuthService)
    }

    override fun inflateEmbeddedView(embedded: ViewGroup, activity: MainActivity, melAuthService: MelAuthService, runningInstance: IMelService?): AndroidServiceGuiController {
        return if (runningInstance == null) {
            RemoteDumpServiceChooserGuiController(melAuthService, activity, embedded)
        } else {
            AndroidDriveEditGuiController(melAuthService, activity, runningInstance, embedded)
        }
    }

    override fun createService(activity: Activity?, melAuthService: MelAuthService?, currentController: AndroidServiceGuiController?) = driveBootLoader.createService(activity, melAuthService, currentController)

    override fun getPermissions(): Array<String> = driveBootLoader.permissions

    override fun getMenuIcon(): Int = R.drawable.icon_notification_dump_legacy

    override fun createNotificationBuilder(context: Context?, melService: IMelService?, melNotification: MelNotification?): NotificationCompat.Builder = driveBootLoader.createNotificationBuilder(context, melService, melNotification)

    override fun createNotificationActivityClass(melService: IMelService, melNotification: MelNotification): Class<*>? {
        val intention = melNotification.intention
        if (intention == DriveStrings.Notifications.INTENTION_PROGRESS || intention == DriveStrings.Notifications.INTENTION_BOOT)
            return MainActivity::class.java
        return null
    }
}
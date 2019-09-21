package de.mein.android.dump

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import de.mein.R
import de.mein.android.MainActivity
import de.mein.android.boot.AndroidBootLoader
import de.mein.android.controller.AndroidServiceGuiController
import de.mein.android.drive.AndroidDriveBootloader
import de.mein.android.drive.controller.AndroidDriveEditGuiController
import de.mein.android.dump.controller.RemoteDumpServiceChooserGuiController
import de.mein.auth.MeinNotification
import de.mein.auth.service.IMeinService
import de.mein.auth.service.MeinAuthService
import de.mein.drive.DriveCreateServiceHelper
import de.mein.drive.data.DriveStrings
import de.mein.drive.service.MeinDriveService
import de.mein.dump.DumpBootloader
import de.mein.dump.DumpCreateServiceHelper

class AndroidDumpBootloader : DumpBootloader(), AndroidBootLoader<MeinDriveService<*>> {

    private val driveBootLoader = object : AndroidDriveBootloader() {
        override fun createCreateServiceHelper(meinAuthService: MeinAuthService): DriveCreateServiceHelper = DumpCreateServiceHelper(meinAuthService)
    }

    override fun inflateEmbeddedView(embedded: ViewGroup, activity: MainActivity, meinAuthService: MeinAuthService, runningInstance: IMeinService?): AndroidServiceGuiController {
        return if (runningInstance == null) {
            RemoteDumpServiceChooserGuiController(meinAuthService, activity, embedded)
        } else {
            AndroidDriveEditGuiController(meinAuthService, activity, runningInstance, embedded)
        }
    }

    override fun createService(activity: Activity?, meinAuthService: MeinAuthService?, currentController: AndroidServiceGuiController?) = driveBootLoader.createService(activity, meinAuthService, currentController)

    override fun getPermissions(): Array<String> = driveBootLoader.permissions

    override fun getMenuIcon(): Int = R.drawable.icon_notification_dump_legacy

    override fun createNotificationBuilder(context: Context?, meinService: IMeinService?, meinNotification: MeinNotification?): NotificationCompat.Builder = driveBootLoader.createNotificationBuilder(context, meinService, meinNotification)

    override fun createNotificationActivityClass(meinService: IMeinService, meinNotification: MeinNotification): Class<*>? {
        val intention = meinNotification.intention
        if (intention == DriveStrings.Notifications.INTENTION_PROGRESS || intention == DriveStrings.Notifications.INTENTION_BOOT)
            return MainActivity::class.java
        return null
    }
}
package de.mel.android.filedump

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import de.mel.R
import de.mel.android.MainActivity
import de.mel.android.boot.AndroidBootLoader
import de.mel.android.controller.AndroidServiceGuiController
import de.mel.android.filesync.AndroidFileSyncBootloader
import de.mel.android.filesync.controller.AndroidFileSyncEditGuiController
import de.mel.android.filedump.controller.RemoteDumpServiceChooserGuiController
import de.mel.auth.MelNotification
import de.mel.auth.service.IMelService
import de.mel.auth.service.MelAuthService
import de.mel.filesync.FileSyncCreateServiceHelper
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.service.MelFileSyncService
import de.mel.dump.DumpBootloader
import de.mel.dump.DumpCreateServiceHelper

class AndroidDumpBootloader : DumpBootloader(), AndroidBootLoader<MelFileSyncService<*>> {

    private val driveBootLoader = object : AndroidFileSyncBootloader() {
        override fun createCreateServiceHelper(melAuthService: MelAuthService): FileSyncCreateServiceHelper = DumpCreateServiceHelper(melAuthService)
    }

    override fun inflateEmbeddedView(embedded: ViewGroup, activity: MainActivity, melAuthService: MelAuthService, runningInstance: IMelService?): AndroidServiceGuiController {
        return if (runningInstance == null) {
            RemoteDumpServiceChooserGuiController(melAuthService, activity, embedded, this)
        } else {
            AndroidFileSyncEditGuiController(melAuthService, activity, runningInstance, embedded)
        }
    }

    override fun createService(activity: Activity?, melAuthService: MelAuthService?, currentController: AndroidServiceGuiController?) = driveBootLoader.createService(activity, melAuthService, currentController)

    override fun getPermissions(): Array<String> = driveBootLoader.permissions

    override fun getMenuIcon(): Int = R.drawable.icon_notification_dump_legacy

    override fun createNotificationBuilder(context: Context?, melService: IMelService?, melNotification: MelNotification?): NotificationCompat.Builder = driveBootLoader.createNotificationBuilder(context, melService, melNotification)

    override fun createNotificationActivityClass(melService: IMelService, melNotification: MelNotification): Class<*>? {
        val intention = melNotification.intention
        if (intention == FileSyncStrings.Notifications.INTENTION_PROGRESS || intention == FileSyncStrings.Notifications.INTENTION_BOOT)
            return MainActivity::class.java
        return null
    }
}
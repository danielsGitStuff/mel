package de.mel.android.filedump.controller

import android.view.ViewGroup
import de.mel.android.MainActivity
import de.mel.android.filesync.controller.RemoteFileSyncServiceChooserGuiController
import de.mel.auth.service.MelAuthService

class RemoteDumpServiceChooserGuiController(melAuthService: MelAuthService, activity: MainActivity, viewGroup: ViewGroup) : RemoteFileSyncServiceChooserGuiController(melAuthService, activity, viewGroup) {
}
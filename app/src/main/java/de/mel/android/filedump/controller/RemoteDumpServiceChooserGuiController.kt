package de.mel.android.filedump.controller

import android.view.ViewGroup
import de.mel.android.MainActivity
import de.mel.android.filesync.controller.RemoteFileSyncServiceChooserGuiController
import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.service.Bootloader
import de.mel.auth.service.MelAuthService
import de.mel.dump.DumpStrings
import de.mel.filesync.data.FileSyncStrings

class RemoteDumpServiceChooserGuiController(melAuthService: MelAuthService, activity: MainActivity, viewGroup: ViewGroup, bootloader: Bootloader<*>) : RemoteFileSyncServiceChooserGuiController(melAuthService, activity, viewGroup, bootloader) {

}
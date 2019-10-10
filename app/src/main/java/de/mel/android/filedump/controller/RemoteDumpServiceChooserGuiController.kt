package de.mel.android.filedump.controller

import android.view.ViewGroup
import de.mel.android.MainActivity
import de.mel.android.filesync.controller.RemoteFileSyncServiceChooserGuiController
import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.service.MelAuthService
import de.mel.dump.DumpStrings
import de.mel.filesync.data.FileSyncStrings

class RemoteDumpServiceChooserGuiController(melAuthService: MelAuthService, activity: MainActivity, viewGroup: ViewGroup) : RemoteFileSyncServiceChooserGuiController(melAuthService, activity, viewGroup) {
    override fun showService(service: ServiceJoinServiceType?): Boolean {
        return service!!.type.v() == DumpStrings.NAME && service.additionalServicePayload != null
    }
}
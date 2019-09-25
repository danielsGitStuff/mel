package de.mel.dump

import de.mel.auth.MelNotification
import de.mel.auth.boot.BootLoaderFX
import de.mel.auth.service.IMelService
import de.mel.drive.data.DriveStrings.Notifications
import de.mel.drive.service.MelDriveServerService
import de.mel.drive.service.MelDriveService
import java.util.*

class DumpFxBootloader : DumpBootloader(), BootLoaderFX<MelDriveService<*>> {
    override fun getCreateFXML(): String =  "de/mel/dumpfx/create.embedded.fxml"

    override fun embedCreateFXML(): Boolean = true

    override fun getEditFXML(melService: MelDriveService<*>?): String = if (melService is MelDriveServerService) "de/mel/drive/editserver.fxml" else "de/mel/drive/editclient.fxml"


    override fun getPopupFXML(melService: IMelService?, melNotification: MelNotification): String? {
        return if (melNotification.getIntention() == Notifications.INTENTION_PROGRESS || melNotification.getIntention() == Notifications.INTENTION_BOOT)
            "de/mel/dumpfx/progress.fxml"
        else
            null
    }

    override fun getIconURL(): String = "de/mel/dumpfx/dump.png"

    override fun getResourceBundle(locale: Locale): ResourceBundle = ResourceBundle.getBundle("de/mel/dumpfx/strings", locale)

}
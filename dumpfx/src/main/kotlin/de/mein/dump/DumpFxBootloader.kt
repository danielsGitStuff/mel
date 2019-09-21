package de.mein.dump

import de.mein.auth.MeinNotification
import de.mein.auth.boot.BootLoaderFX
import de.mein.auth.service.IMeinService
import de.mein.drive.data.DriveStrings.Notifications
import de.mein.drive.service.MeinDriveServerService
import de.mein.drive.service.MeinDriveService
import java.util.*

class DumpFxBootloader : DumpBootloader(), BootLoaderFX<MeinDriveService<*>> {
    override fun getCreateFXML(): String =  "de/mel/dumpfx/create.embedded.fxml"

    override fun embedCreateFXML(): Boolean = true

    override fun getEditFXML(meinService: MeinDriveService<*>?): String = if (meinService is MeinDriveServerService) "de/mein/drive/editserver.fxml" else "de/mein/drive/editclient.fxml"


    override fun getPopupFXML(meinService: IMeinService?, meinNotification: MeinNotification): String? {
        return if (meinNotification.getIntention() == Notifications.INTENTION_PROGRESS || meinNotification.getIntention() == Notifications.INTENTION_BOOT)
            "de/mel/dumpfx/progress.fxml"
        else
            null
    }

    override fun getIconURL(): String = "de/mel/dumpfx/dump.png"

    override fun getResourceBundle(locale: Locale): ResourceBundle = ResourceBundle.getBundle("de/mel/dumpfx/strings", locale)

}
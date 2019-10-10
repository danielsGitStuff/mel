package de.mel.dump

import de.mel.auth.MelNotification
import de.mel.auth.boot.BootLoaderFX
import de.mel.auth.service.IMelService
import de.mel.filesync.data.FileSyncStrings.Notifications
import de.mel.filesync.service.MelFileSyncServerService
import de.mel.filesync.service.MelFileSyncService
import java.util.*

class DumpFxBootloader : DumpBootloader(), BootLoaderFX<MelFileSyncService<*>> {
    override fun getCreateFXML(): String =  "de/mel/filesyncfx/create.embedded.fxml"

    override fun embedCreateFXML(): Boolean = true

    override fun getEditFXML(melService: MelFileSyncService<*>?): String = if (melService is MelFileSyncServerService) "de/mel/filesyncfx/editserver.fxml" else "de/mel/filesyncfx/editclient.fxml"


    override fun getPopupFXML(melService: IMelService?, melNotification: MelNotification): String? {
        return if (melNotification.getIntention() == Notifications.INTENTION_PROGRESS || melNotification.getIntention() == Notifications.INTENTION_BOOT)
            "de/mel/filedumpfx/progress.fxml"
        else
            null
    }

    override fun getIconURL(): String = "de/mel/filedumpfx/dump.png"

    override fun getResourceBundle(locale: Locale): ResourceBundle = ResourceBundle.getBundle("de/mel/filedumpfx/strings", locale)

}
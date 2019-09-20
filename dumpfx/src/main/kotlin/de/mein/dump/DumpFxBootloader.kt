package de.mein.dump

import de.mein.auth.MeinNotification
import de.mein.auth.boot.BootLoaderFX
import de.mein.auth.service.IMeinService
import de.mein.drive.DriveBootloader
import de.mein.drive.data.DriveSettings
import de.mein.drive.service.MeinDriveService
import java.io.File
import java.util.*

class DumpFxBootloader : DumpBootloader(), BootLoaderFX<MeinDriveService<*>> {
    override fun getCreateFXML(): String  = "!NEIN!"

    override fun embedCreateFXML(): Boolean = true

    override fun getEditFXML(meinService: MeinDriveService<*>?): String = "!NEIN!"

    override fun getPopupFXML(meinService: IMeinService?, dataObject: MeinNotification?): String = "!NEIN!"

    override fun getIconURL(): String = "de/mel/dumpfx/dump.png"

    override fun getResourceBundle(locale: Locale): ResourceBundle = ResourceBundle.getBundle("de/mel/dumpfx/strings", locale)

}
package de.mel.sms

import de.mel.auth.MelNotification
import de.mel.auth.boot.BootLoaderFX
import de.mel.auth.service.IMelService
import de.mel.sms.service.SmsService
import java.util.Locale
import java.util.ResourceBundle

class SmsFXBootloader : SmsBootloader(), BootLoaderFX<SmsService> {
    override fun getCreateFXML(): String = "de/mel/sms/create.embedded.fxml"

    override fun getPropertiesFXML(melService: SmsService): String? = null

    override fun embedCreateFXML(): Boolean = true

    override fun getEditFXML(melService: SmsService): String = "de/mel/sms/edit.fxml"

    override fun getPopupFXML(melService: IMelService, dataObject: MelNotification): String? = null

    override fun getIconURL(): String = "de/mel/sms/sms.png"

    override fun getResourceBundle(locale: Locale): ResourceBundle =
        ResourceBundle.getBundle("de/mel/sms/strings", locale)
}

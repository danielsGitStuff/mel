package de.mel.sms.gui

import de.mel.auth.gui.EmbeddedServiceSettingsFX
import de.mel.auth.service.MelAuthAdminFX
import de.mel.sms.SmsBootloader
import de.mel.sms.crypto.SmsCrypto
import de.mel.sms.data.SmsSettings
import de.mel.sms.data.SmsStrings
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.TextField

class SmsFXCreateController : EmbeddedServiceSettingsFX() {

    @FXML
    private lateinit var txtName: TextField

    @FXML
    private lateinit var txtSecret: TextField

    @FXML
    private lateinit var chkShareSecret: CheckBox

    @FXML
    fun onGenerateSecret() {
        txtSecret.text = SmsCrypto.generateRandomSecret()
    }

    override fun onPrimaryClicked(melAuthAdminFX: MelAuthAdminFX): Boolean {
        val name = txtName.text?.trim().orEmpty().ifEmpty { SmsStrings.NAME }
        val bootloader = melAuthService.melBoot.getBootLoader(SmsStrings.TYPE) as SmsBootloader
        val settings = SmsSettings()
        if (isServerSelected) {
            settings.role = SmsStrings.ROLE_SERVER
        } else {
            settings.role = SmsStrings.ROLE_CLIENT
            settings.serverCertId = selectedCertificate?.id?.v()
            settings.serverServiceUuid = selectedService?.uuid?.v()
            settings.shareSecretWithServer = chkShareSecret.isSelected
        }
        val secret = txtSecret.text?.trim()
        if (!secret.isNullOrEmpty()) {
            settings.secret = secret
        }
        bootloader.createService(name, settings)
        return false
    }

    override fun init() {}

    override fun getTitle(): String = getString("create.title")
}

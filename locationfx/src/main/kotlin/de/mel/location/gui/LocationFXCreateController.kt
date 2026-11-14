package de.mel.location.gui

import de.mel.auth.gui.AuthSettingsFX
import de.mel.auth.service.MelAuthAdminFX
import de.mel.location.LocationBootloader
import de.mel.location.data.LocationSettings
import de.mel.location.data.LocationStrings
import javafx.fxml.FXML
import javafx.scene.control.TextField

class LocationFXCreateController : AuthSettingsFX() {

    @FXML
    private lateinit var txtName: TextField

    @FXML
    private lateinit var txtPort: TextField

    @FXML
    private lateinit var txtBind: TextField

    @FXML
    private lateinit var txtUser: TextField

    @FXML
    private lateinit var txtPass: TextField

    override fun onPrimaryClicked(melAuthAdminFX: MelAuthAdminFX): Boolean {
        val name = txtName.text?.trim().orEmpty().ifEmpty { LocationStrings.NAME }
        val settings = LocationSettings().apply {
            port = txtPort.text?.trim()?.toIntOrNull() ?: LocationStrings.DEFAULT_PORT
            bindAddress = txtBind.text?.trim().orEmpty().ifEmpty { "0.0.0.0" }
            username = txtUser.text?.trim().orEmpty()
            password = txtPass.text ?: ""
        }
        val bootloader = melAuthService.melBoot.getBootLoader(LocationStrings.TYPE) as LocationBootloader
        bootloader.createService(name, settings)
        return false
    }

    override fun init() {}

    override fun getTitle(): String = getString("create.title")

    override fun configureParentGui(melAuthAdminFX: MelAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("create.apply"))
        melAuthAdminFX.showPrimaryButtonOnly()
    }
}

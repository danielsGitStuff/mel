package de.mel.location.gui

import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.gui.ServiceSettingsFX
import de.mel.auth.service.MelAuthAdminFX
import de.mel.location.data.LocationStrings
import de.mel.location.service.LocationService
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import java.text.SimpleDateFormat
import java.util.Date

class LocationFXEditController : ServiceSettingsFX<LocationService>() {

    @FXML
    private lateinit var lblEndpoint: Label

    @FXML
    private lateinit var txtPort: TextField

    @FXML
    private lateinit var txtBind: TextField

    @FXML
    private lateinit var txtUser: TextField

    @FXML
    private lateinit var txtPass: TextField

    @FXML
    private lateinit var txtFilterTid: TextField

    @FXML
    private lateinit var listPoints: ListView<String>

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    override fun init() {
        txtFilterTid.textProperty().addListener { _, _, _ -> reloadPoints() }
    }

    override fun feed(serviceJoinServiceType: ServiceJoinServiceType) {
        super.feed(serviceJoinServiceType)
        val s = service.settings
        txtPort.text = s.port.toString()
        txtBind.text = s.bindAddress
        txtUser.text = s.username
        txtPass.text = s.password
        lblEndpoint.text = "OwnTracks HTTP: http://${s.bindAddress}:${s.port}/pub"
        service.addOnChangedListener { Platform.runLater { reloadPoints() } }
        reloadPoints()
    }

    private fun reloadPoints() {
        val svc = service ?: return
        val tidFilter = txtFilterTid.text?.trim()?.lowercase().orEmpty()
        val lines = svc.loadRecent(200)
            .filter { tidFilter.isEmpty() || it.tid.v().lowercase().contains(tidFilter) }
            .map { pt ->
                val ts = dateFmt.format(Date(pt.tst.v() * 1000L))
                "$ts  [${pt.tid.v()}]  ${pt.lat.v()}, ${pt.lon.v()}  (acc=${pt.acc.v() ?: "-"}m, batt=${pt.batt.v() ?: "-"}%)"
            }
        listPoints.items.setAll(lines)
    }

    override fun onPrimaryClicked(melAuthAdminFX: MelAuthAdminFX): Boolean {
        val port = txtPort.text?.trim()?.toIntOrNull() ?: LocationStrings.DEFAULT_PORT
        val bind = txtBind.text?.trim().orEmpty().ifEmpty { "0.0.0.0" }
        service.updateSettings(port, bind, txtUser.text?.trim().orEmpty(), txtPass.text ?: "")
        lblEndpoint.text = "OwnTracks HTTP: http://$bind:$port/pub"
        return true
    }

    override fun getTitle(): String = getString("edit.title")

    override fun configureParentGui(melAuthAdminFX: MelAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("edit.apply"))
        melAuthAdminFX.setSecondaryButtonText(getString("edit.delete"))
    }
}

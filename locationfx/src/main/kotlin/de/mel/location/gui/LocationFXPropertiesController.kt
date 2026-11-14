package de.mel.location.gui

import de.mel.auth.data.db.ServiceJoinServiceType
import de.mel.auth.gui.ServicePropertiesFX
import de.mel.location.service.LocationService
import javafx.fxml.FXML
import javafx.scene.control.Label

class LocationFXPropertiesController : ServicePropertiesFX<LocationService>() {

    @FXML
    private lateinit var lblEndpoint: Label

    @FXML
    private lateinit var lblStats: Label

    override fun init() {}

    override fun getTitle(): String = getString("edit.title")

    override fun feed(serviceJoinServiceType: ServiceJoinServiceType) {
        super.feed(serviceJoinServiceType)
        val svc = service ?: return
        val s = svc.settings
        lblEndpoint.text = "OwnTracks HTTP: http://${s.bindAddress}:${s.port}/pub"
        lblStats.text = "Stored points (recent batch): ${svc.loadRecent(200).size}"
    }
}

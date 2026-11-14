package de.mel.location

import de.mel.auth.MelNotification
import de.mel.auth.boot.BootLoaderFX
import de.mel.auth.service.IMelService
import de.mel.location.service.LocationService
import java.util.Locale
import java.util.ResourceBundle

class LocationFXBootloader : LocationBootloader(), BootLoaderFX<LocationService> {
    override fun getCreateFXML(): String = "de/mel/location/create.fxml"

    override fun getPropertiesFXML(melService: LocationService): String = "de/mel/location/properties.fxml"

    override fun embedCreateFXML(): Boolean = false

    override fun getEditFXML(melService: LocationService): String = "de/mel/location/edit.fxml"

    override fun getPopupFXML(melService: IMelService, dataObject: MelNotification): String? = null

    override fun getIconURL(): String = "de/mel/location/location.png"

    override fun getResourceBundle(locale: Locale): ResourceBundle =
        ResourceBundle.getBundle("de/mel/location/strings", locale)
}

package de.mel.auth.boot;

import de.mel.auth.MelNotification;
import de.mel.auth.service.IMelService;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Created by xor on 9/21/16.
 */
public interface BootLoaderFX<T extends IMelService> {
    String getCreateFXML();

    /**
     * if true fxml will be wrapped in a RemoteServiceChooserFX
     * @return
     */
    boolean embedCreateFXML();

    String getEditFXML(T melService);

    String getPopupFXML(IMelService melService, MelNotification dataObject);

    String getIconURL();

    ResourceBundle getResourceBundle(Locale locale);
}

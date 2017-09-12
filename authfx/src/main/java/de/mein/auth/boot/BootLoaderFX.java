package de.mein.auth.boot;

import de.mein.auth.MeinNotification;
import de.mein.auth.service.IMeinService;

/**
 * Created by xor on 9/21/16.
 */
public interface BootLoaderFX<T extends IMeinService> {
    String getCreateFXML();

    String getEditFXML(T meinService);

    String getPopupFXML(IMeinService meinService, MeinNotification dataObject);

}

package de.mein.auth.gui;

import de.mein.auth.service.MeinService;

/**
 * Created by xor on 5/30/17.
 */
public interface PopupContentFX {
    /**
     * Return null if everything is fine. if you got an complaint for the user return it as a String.
     * @return
     */
    String onOkCLicked();

    void init(MeinService meinService, Object msgObject);
}

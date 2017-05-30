package de.mein.auth.gui;

import de.mein.auth.service.MeinService;

/**
 * Created by xor on 5/30/17.
 */
public interface PopupContentFX {
    boolean onOkCLicked();

    void init(MeinService meinService, Object msgObject);
}

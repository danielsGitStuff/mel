package de.mein.auth.gui;

import de.mein.auth.MeinNotification;
import de.mein.auth.service.MeinAuthService;
import javafx.stage.Stage;

/**
 * Created by xor on 5/30/17.
 */
public abstract class PopupContentFX {
    protected Stage stage;

    /**
     * Return null if everything is fine. if you got an complaint for the user return it as a String.
     *
     * @return
     */
    public abstract String onOkCLicked();

    public final void init(Stage stage, MeinAuthService meinAuthService, MeinNotification notification) {
        this.stage = stage;
        this.stage.setTitle(notification.getTitle());
        initImpl(stage, meinAuthService, notification);
    }

    public abstract void initImpl(Stage stage, MeinAuthService meinAuthService, MeinNotification notification);

}

package de.mel.auth.gui;

import de.mel.auth.MelNotification;
import de.mel.auth.service.MelAuthService;
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

    public final void init(Stage stage, MelAuthService melAuthService, MelNotification notification) {
        this.stage = stage;
        this.stage.setTitle(notification.getTitle());
        initImpl(stage, melAuthService, notification);
    }

    public abstract void initImpl(Stage stage, MelAuthService melAuthService, MelNotification notification);

}

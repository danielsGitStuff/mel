package de.mein.auth.gui;

import de.mein.auth.service.MeinService;
import javafx.stage.Stage;

/**
 * Created by xor on 5/30/17.
 */
public abstract class PopupContentFX {
    protected Stage stage;

    /**
     * Return null if everything is fine. if you got an complaint for the user return it as a String.
     * @return
     */
    public abstract String onOkCLicked();

    public abstract void init(MeinService meinService, Object msgObject);

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}

package de.mein.auth.gui;

import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.service.MeinAuthService;
import javafx.stage.Stage;

/**
 * Created by xor on 18.09.2016.
 */
public abstract class AuthSettingsFX {

    protected MeinAuthService meinAuthService;
    protected Stage stage;

    /**
     * called when bottom right button is clicked (usually 'Apply')
     * @return
     */
    public abstract boolean onPrimaryClicked();

    public void setMeinAuthService(MeinAuthService meinAuthService){
        this.meinAuthService = meinAuthService;
        this.init();
    }

    /**
     * this is called once everything (FXML and MeinAuthService) are set up on this instance.
     * it is now time to init your GUI with appropriate values
     */
    public abstract void init();


    public abstract String getTitle();

    /**
     * override to hide buttons or something
     * @param meinAuthAdminFX
     */
    public abstract void configureParentGui(MeinAuthAdminFX meinAuthAdminFX);

    /**
     * called when bottom left button is clicked.
     */
    public void onSecondaryClicked() {

    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }
}

package de.mel.auth.gui;

import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.service.MelAuthService;
import javafx.fxml.Initializable;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by xor on 18.09.2016.
 */
public abstract class AuthSettingsFX implements Initializable {

    protected MelAuthService melAuthService;
    protected Stage stage;
    protected ResourceBundle resources;

    protected String getString(String key) {
        return resources.getString(key);
    }

    /**
     * called when bottom right button is clicked (usually 'Apply')
     *
     * @return
     */
    public abstract boolean onPrimaryClicked();

    public void setMelAuthService(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
        this.init();
    }

    /**
     * this is called once everything (FXML and MelAuthService) are set up on this instance.
     * it is now time to init your GUI with appropriate values
     */
    public abstract void init();


    public abstract String getTitle();

    /**
     * override to hide buttons or something
     *
     * @param melAuthAdminFX
     */
    public abstract void configureParentGui(MelAuthAdminFX melAuthAdminFX);

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.resources = resources;
    }
}

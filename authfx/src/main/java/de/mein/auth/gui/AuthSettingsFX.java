package de.mein.auth.gui;

import de.mein.auth.service.MeinAuthFX;
import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 18.09.2016.
 */
public abstract class AuthSettingsFX {

    protected MeinAuthService meinAuthService;

    public abstract void onApplyClicked();

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
     * @param meinAuthFX
     */
    public void configureParentGui(MeinAuthFX meinAuthFX){

    }
}

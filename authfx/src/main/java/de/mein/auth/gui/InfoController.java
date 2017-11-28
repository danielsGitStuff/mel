package de.mein.auth.gui;

import de.mein.auth.service.MeinAuthAdminFX;

public class InfoController extends  AuthSettingsFX{
    @Override
    public void onApplyClicked() {

    }

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return "Info goes here";
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.hideBottomButtons();
    }
}

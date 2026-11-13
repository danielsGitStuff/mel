package de.mel.filesync.gui;

import de.mel.auth.service.MelAuthAdminFX;

/**
 * Created by xor on 10/27/16.
 */
public class FileSyncFXEditServerController extends FileSyncFXEditBaseController {
    @Override
    public boolean onPrimaryClicked(MelAuthAdminFX melAuthAdminFX) {
        applyName();
        return false;
    }

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return getString("edit.title.server");
    }
}

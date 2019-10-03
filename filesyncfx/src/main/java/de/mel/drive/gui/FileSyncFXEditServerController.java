package de.mel.drive.gui;

/**
 * Created by xor on 10/27/16.
 */
public class FileSyncFXEditServerController extends FileSyncFXEditBaseController {
    @Override
    public boolean onPrimaryClicked() {
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

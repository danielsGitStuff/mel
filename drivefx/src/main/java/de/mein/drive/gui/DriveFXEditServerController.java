package de.mein.drive.gui;

/**
 * Created by xor on 10/27/16.
 */
public class DriveFXEditServerController extends DriveFXEditBaseController {
    @Override
    public void onApplyClicked() {
        applyName();
    }

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return "Edit Drive server settings";
    }
}

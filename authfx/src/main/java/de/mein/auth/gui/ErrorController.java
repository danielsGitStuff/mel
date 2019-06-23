package de.mein.auth.gui;

import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceError;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.tools.N;
import de.mein.core.serialize.serialize.tools.StringBuilder;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class ErrorController extends AuthSettingsFX {
    @FXML
    private Label lblCaption;
    @FXML
    private TextArea txtJson;

    @Override
    public boolean onPrimaryClicked() {

        return false;
    }

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return "ALAAAARM!";
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.hideBottomButtons();
    }

    public void showError(Service service) {
        StringBuilder b = new StringBuilder();
        N.r(() -> {
            ServiceError error = service.getLastError();
            b.append("Exception: ").append(error.getExceptionClass()).lineBreak();
            b.append("Message: ").append(error.getExceptionMessage()).lineBreak();
            if (error.getStacktrace() != null) {
                b.append("Stacktrace: ").lineBreak();
                N.forEach(error.getStacktrace(), s -> b.append("   ").append(s).lineBreak());
            }
            b.append("Variant: ").append(error.getVariant()).lineBreak();
            b.append("Version: ").append(error.getVersion()).lineBreak();
        });
        txtJson.setText(b.toString());
    }
}

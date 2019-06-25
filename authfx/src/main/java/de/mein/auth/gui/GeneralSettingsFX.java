package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.MeinAuthAdminFX;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by xor on 18.09.2016.
 */
public class GeneralSettingsFX extends AuthSettingsFX {

    @FXML
    protected TextField txtWorkingDirectory;
    @FXML
    private TextField txtName, txtPort, txtSslPort;

    @Override
    public boolean onPrimaryClicked() {
        Lok.debug("GeneralSettingsFX.onPrimaryClicked");
        MeinAuthSettings settings = meinAuthService.getSettings();
        settings.setName(txtName.getText());
        settings.setPort(Integer.parseInt(txtSslPort.getText()));
        settings.setDeliveryPort(Integer.parseInt(txtPort.getText()));
        settings.setWorkingDirectory(new File(txtWorkingDirectory.getText()));
        try {
            settings.save();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private TextFormatter.Change numbersOnly(TextFormatter.Change change) {
        String text = change.getText();
        if (text.matches("[0-9]*")) {
            return change;
        }
        return null;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
        txtPort.setTextFormatter(new TextFormatter<>(this::numbersOnly));
        txtSslPort.setTextFormatter(new TextFormatter<>(this::numbersOnly));
        Lok.debug("GeneralSettingsFX.initialize");
    }


    @Override
    public void init() {
        MeinAuthSettings settings = meinAuthService.getSettings();
        txtWorkingDirectory.setText(settings.getWorkingDirectory().getAbsolutePath());
        txtName.setText(settings.getName());
        txtPort.setText(String.valueOf(settings.getDeliveryPort()));
        txtSslPort.setText(String.valueOf(settings.getPort()));
    }

    @Override
    public String getTitle() {
        return getString("settings.title");
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.setPrimaryButtonText(getString("settings.btnApply"));
        meinAuthAdminFX.showPrimaryButtonOnly();
    }
}

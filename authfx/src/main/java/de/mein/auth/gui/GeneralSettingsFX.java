package de.mein.auth.gui;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.MeinAuthAdminFX;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by xor on 18.09.2016.
 */
public class GeneralSettingsFX extends AuthSettingsFX implements Initializable {

    @FXML
    protected TextField txtWorkingDirectory;
    @FXML
    private TextField txtName, txtPort, txtSslPort, txtGreeting;

    @Override
    public void onPrimaryClicked() {
        System.out.println("GeneralSettingsFX.onPrimaryClicked");
        MeinAuthSettings settings = meinAuthService.getSettings();
        settings.setName(txtName.getText());
        settings.setPort(Integer.parseInt(txtSslPort.getText()));
        settings.setDeliveryPort(Integer.parseInt(txtPort.getText()));
        settings.setWorkingDirectory(new File(txtWorkingDirectory.getText()));
        settings.setGreeting(txtGreeting.getText());
        try {
            settings.save();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        txtPort.setTextFormatter(new TextFormatter<>(change -> numbersOnly(change)));
        txtSslPort.setTextFormatter(new TextFormatter<>(change -> numbersOnly(change)));
        System.out.println("GeneralSettingsFX.initialize");
    }


    @Override
    public void init() {
        MeinAuthSettings settings = meinAuthService.getSettings();
        txtWorkingDirectory.setText(settings.getWorkingDirectory().getAbsolutePath());
        txtName.setText(settings.getName());
        txtPort.setText(String.valueOf(settings.getDeliveryPort()));
        txtSslPort.setText(String.valueOf(settings.getPort()));
        txtGreeting.setText(settings.getGreeting());
    }

    @Override
    public String getTitle() {
        return "General stuff";
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.setPrimaryButtonText("Apply");
        meinAuthAdminFX.showPrimaryButtonOnly();
    }
}

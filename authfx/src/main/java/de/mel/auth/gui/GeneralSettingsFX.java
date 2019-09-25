package de.mel.auth.gui;

import de.mel.Lok;
import de.mel.LokImpl;
import de.mel.auth.FxApp;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.tools.DBLokImpl;
import de.mel.auth.tools.N;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by xor on 18.09.2016.
 */
public class GeneralSettingsFX extends AuthSettingsFX {
    @FXML
    private CheckBox cbLogToDB;
    @FXML
    protected ComboBox comboLang;
    @FXML
    protected TextField txtWorkingDirectory;
    @FXML
    private TextField txtName, txtPort, txtSslPort, txtLogToDB;

    @Override
    public boolean onPrimaryClicked() {
        Lok.debug("GeneralSettingsFX.onPrimaryClicked");
        MelAuthSettings settings = melAuthService.getSettings();
        settings.setName(txtName.getText());
        settings.setPort(Integer.parseInt(txtSslPort.getText()));
        settings.setDeliveryPort(Integer.parseInt(txtPort.getText()));
        settings.setWorkingDirectory(new File(txtWorkingDirectory.getText()));
        if (cbLogToDB.selectedProperty().get()) {
            settings.setPreserveLogLinesInDb(Long.parseLong(txtLogToDB.getText()));
            N.r(() -> DBLokImpl.setupDBLockImpl(new File(settings.getWorkingDirectory(), "log.db"), settings.getPreserveLogLinesInDb()));
        } else {
            settings.setPreserveLogLinesInDb(0L);
            Lok.setLokImpl(new LokImpl().setup(0, true));
        }
        String language = comboLang.getSelectionModel().getSelectedItem().toString();
        settings.setLanguage(language);
        try {
            settings.save();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private TextFormatter.Change positiveNumbersOnly(TextFormatter.Change change) {
        String text = change.getText();
        if (text.matches("[0-9]*")) {
            return change;
        }
        return null;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
        txtPort.setTextFormatter(new TextFormatter<>(this::positiveNumbersOnly));
        txtSslPort.setTextFormatter(new TextFormatter<>(this::positiveNumbersOnly));
        txtLogToDB.setTextFormatter(new TextFormatter<Object>(this::positiveNumbersOnly));
        txtLogToDB.textProperty().addListener((observable, oldValue, newValue) -> {
            Long value = N.result(() -> Long.parseLong(newValue), 0L);
            if (value < 1L) {
                cbLogToDB.selectedProperty().set(false);
            } else
                cbLogToDB.selectedProperty().set(true);
        });
        cbLogToDB.selectedProperty().addListener((observable, oldValue, newValue) -> {
            txtLogToDB.setVisible(newValue);
        });
        Lok.debug("GeneralSettingsFX.initialize");
    }


    @Override
    public void init() {
        MelAuthSettings settings = melAuthService.getSettings();
        txtWorkingDirectory.setText(settings.getWorkingDirectory().getAbsolutePath());
        txtName.setText(settings.getName());
        txtPort.setText(String.valueOf(settings.getDeliveryPort()));
        txtSslPort.setText(String.valueOf(settings.getPort()));
        comboLang.getItems().addAll("de", "en");
        comboLang.getSelectionModel().select(settings.getLanguage());
        comboLang.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != oldValue) {
                FxApp.showInfoDialog(getString("settings.alert.title"), getString("settings.alert.text"));
            }
        });
        Long preservedLines = settings.getPreserveLogLinesInDb();
        txtLogToDB.setText(String.valueOf(preservedLines));
        boolean logToDB = preservedLines > 0L;
        txtLogToDB.setVisible(logToDB);
        cbLogToDB.selectedProperty().setValue(logToDB);
    }

    @Override
    public String getTitle() {
        return getString("settings.title");
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("settings.btnApply"));
        melAuthAdminFX.showPrimaryButtonOnly();
    }
}

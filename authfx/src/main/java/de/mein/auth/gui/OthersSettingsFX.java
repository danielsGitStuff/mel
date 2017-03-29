package de.mein.auth.gui;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.gui.controls.CertListCell;
import de.mein.auth.service.MeinAuthFX;
import de.mein.auth.tools.NoTryRunner;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;

/**
 * Created by xor on 3/27/17.
 */
public class OthersSettingsFX extends AuthSettingsFX {
    @FXML
    private ListView<Certificate> listCerts;
    @FXML
    private Button btnDelete;

    private Certificate selectedCert;

    @Override
    public void onApplyClicked() {

    }

    @Override
    public void init() {
        listCerts.setCellFactory(param -> new CertListCell());
        listCerts.getItems().clear();
        showCerts();
        listCerts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                selectedCert = selected;
            } else {
                selectedCert = null;
            }
        });
        btnDelete.setOnAction(event -> NoTryRunner.run(() -> {
            if (selectedCert != null) {
                meinAuthService.getCertificateManager().deleteCertificate(selectedCert);
                showCerts();
            }
        }));
    }

    private void showCerts() {
        NoTryRunner.run(() -> {
            selectedCert = null;
            listCerts.getItems().addAll(meinAuthService.getTrustedCertificates());
        });
    }

    @Override
    public String getTitle() {
        return "Other (known) instances";
    }

    @Override
    public void configureParentGui(MeinAuthFX meinAuthFX) {
        meinAuthFX.hideBottomButtons();
    }
}

package de.mel.auth.gui;

import de.mel.auth.data.db.Certificate;
import de.mel.auth.gui.controls.CertListCell;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.tools.N;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

/**
 * Created by xor on 3/27/17.
 */
public class OthersSettingsFX extends AuthSettingsFX {
    @FXML
    private ListView<Certificate> listCerts;

    private Certificate selectedCert;

    @Override
    public boolean onPrimaryClicked() {
        N.r(() -> {
            if (selectedCert != null) {
                melAuthService.getCertificateManager().deleteCertificate(selectedCert);
                showCerts();
            }
        });
        return false;
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
    }

    private void showCerts() {
        N.r(() -> {
            selectedCert = null;
            listCerts.getItems().addAll(melAuthService.getTrustedCertificates());
        });
    }

    @Override
    public String getTitle() {
        return getString("others.title");
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("delete"));
        melAuthAdminFX.showPrimaryButtonOnly();
    }
}

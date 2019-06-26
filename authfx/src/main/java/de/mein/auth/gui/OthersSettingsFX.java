package de.mein.auth.gui;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.gui.controls.CertListCell;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.tools.N;
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
                meinAuthService.getCertificateManager().deleteCertificate(selectedCert);
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
            listCerts.getItems().addAll(meinAuthService.getTrustedCertificates());
        });
    }

    @Override
    public String getTitle() {
        return getString("others.title");
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.setPrimaryButtonText(getString("delete"));
        meinAuthAdminFX.showPrimaryButtonOnly();
    }
}

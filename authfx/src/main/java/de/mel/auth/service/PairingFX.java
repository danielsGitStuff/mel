package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.gui.AuthSettingsFX;
import de.mel.auth.gui.controls.CertListCell;
import de.mel.auth.gui.controls.UnkownListCell;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.tools.N;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.jdeferred.Promise;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by xor on 10/25/16.
 */
public class PairingFX extends AuthSettingsFX {
    private NetworkEnvironment environment;
    @FXML
    private ListView<NetworkEnvironment.UnknownAuthInstance> listAll;
    @FXML
    private ListView<Certificate> listKnown;
    @FXML
    private TextField txtAddress, txtPort, txtCertDeliveryPort;

    @Override
    public boolean onPrimaryClicked() {
        N.r(() -> {
            Promise<MelValidationProcess, Exception, Void> promise = melAuthService.connect(txtAddress.getText(), Integer.parseInt(txtPort.getText()), Integer.parseInt(txtCertDeliveryPort.getText()), true);
            promise.done(result -> discover());
        });
        return false;
    }

    @Override
    public void init() {
        listAll.setCellFactory(param -> new UnkownListCell());
        listKnown.setCellFactory(param -> new CertListCell());
        environment = melAuthService.getNetworkEnvironment();
        discover();
    }

    @Override
    public String getTitle() {
        return getString("pairing.title");
    }

    private void discover() {
        environment.deleteObservers();
        environment.deleteObservers();
        environment.addObserver((o, arg) -> {
            Lok.debug("PairingFX.init");
            listAll.getItems().clear();
            listAll.getItems().addAll(environment.getUnknownAuthInstances());
            listKnown.getItems().clear();
            for (Long certId : environment.getCertificateIds()) {
                try {
                    Lok.debug("PairingFX.discover");
                    Certificate certificate = melAuthService.getCertificateManager().getCertificateById(certId);
                    listKnown.getItems().add(certificate);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
        melAuthService.discoverNetworkEnvironment();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
        listAll.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                txtAddress.setText(selected.getAddress());
                txtCertDeliveryPort.setText(Integer.toString(selected.getPortCert()));
                txtPort.setText(Integer.toString(selected.getPort()));
            }
        });
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("pairing.pair"));
        melAuthAdminFX.showPrimaryButtonOnly();
    }
}

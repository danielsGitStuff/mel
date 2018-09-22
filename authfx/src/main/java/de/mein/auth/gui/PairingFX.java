package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.gui.controls.CertListCell;
import de.mein.auth.gui.controls.UnkownListCell;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.jdeferred.Promise;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by xor on 10/25/16.
 */
public class PairingFX extends AuthSettingsFX implements Initializable {
    private NetworkEnvironment environment;
    @FXML
    private ListView<NetworkEnvironment.UnknownAuthInstance> listAll;
    @FXML
    private ListView<Certificate> listKnown;
    @FXML
    private TextField txtAddress, txtPort, txtCertDeliveryPort;

    @Override
    public void onPrimaryClicked() {
        N.r(() -> {
            Promise<MeinValidationProcess, Exception, Void> promise = meinAuthService.connect(txtAddress.getText(), Integer.parseInt(txtPort.getText()), Integer.parseInt(txtCertDeliveryPort.getText()), true);
            promise.done(result -> discover());
        });
    }

    @Override
    public void init() {
        listAll.setCellFactory(param -> new UnkownListCell());
        listKnown.setCellFactory(param -> new CertListCell());
        environment = meinAuthService.getNetworkEnvironment();
        discover();
    }

    @Override
    public String getTitle() {
        return "Find other instances";
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
                    Certificate certificate = meinAuthService.getCertificateManager().getCertificateById(certId);
                    listKnown.getItems().add(certificate);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
        meinAuthService.discoverNetworkEnvironment();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        listAll.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                txtAddress.setText(selected.getAddress());
                txtCertDeliveryPort.setText(Integer.toString(selected.getPortCert()));
                txtPort.setText(Integer.toString(selected.getPort()));
            }
        });
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.setPrimaryButtonText("Apply");
        meinAuthAdminFX.showPrimaryButtonOnly();
    }
}

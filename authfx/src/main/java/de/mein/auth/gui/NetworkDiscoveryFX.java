package de.mein.auth.gui;

import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.gui.controls.CertListCell;
import de.mein.auth.gui.controls.UnkownListCell;
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
public class NetworkDiscoveryFX extends AuthSettingsFX implements Initializable {
    private NetworkEnvironment environment;
    @FXML
    private ListView<NetworkEnvironment.UnknownAuthInstance> listAll;
    @FXML
    private ListView<Certificate> listKnown;
    @FXML
    private Button btnRegister;
    @FXML
    private TextField txtAddress, txtPort, txtCertDeliveryPort;

    @Override
    public void onApplyClicked() {

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
            System.out.println("NetworkDiscoveryFX.init");
            listAll.getItems().clear();
            listAll.getItems().addAll(environment.getUnknownAuthInstances());
            listKnown.getItems().clear();
            for (Long certId : environment.getCertificateIds()) {
                try {
                    System.out.println("NetworkDiscoveryFX.discover");
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
        btnRegister.setOnAction(event -> N.r(() -> {
            Promise<MeinValidationProcess, Exception, Void> promise = meinAuthService.connect(null, txtAddress.getText(), Integer.parseInt(txtPort.getText()), Integer.parseInt(txtCertDeliveryPort.getText()), true);
            promise.done(result -> discover());
        }));
    }
}

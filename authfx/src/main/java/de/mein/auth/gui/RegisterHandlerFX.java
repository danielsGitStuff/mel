package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ResourceBundle;

/**
 * Created by xor on 4/22/16.
 */
public class RegisterHandlerFX implements IRegisterHandler, Initializable {

    @FXML
    private Button btnAccept;
    @FXML
    private Button btnReject;
    @FXML
    private TextArea txtMyCert;
    @FXML
    private TextArea txtCert;
    @FXML
    private Label lblMyCert;
    @FXML
    private Label lblCert;

    private Certificate myCertificate, certificate;
    private IRegisterHandlerListener listener;
    private Stage stage;
    private MeinRequest request;

    public RegisterHandlerFX setRequest(MeinRequest request) {
        this.request = request;
        return this;
    }

    public void setMyCertificate(Certificate myCertificate) {
        this.myCertificate = myCertificate;
        try {
            X509Certificate myX509Certificate = CertificateManager.loadX509CertificateFromBytes(myCertificate.getCertificate().v());
            txtMyCert.setText(myX509Certificate.toString());
            lblMyCert.setText("My Certificate (" + myCertificate.getName().v() + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
        try {
            X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(certificate.getCertificate().v());
            txtCert.setText(x509Certificate.toString());
            lblCert.setText("Incoming Certificate (" + certificate.getName().v() + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setListener(IRegisterHandlerListener listener) {
        this.listener = listener;
        btnAccept.setOnAction(e -> {
            Lok.debug("RegisterHandlerFX.initialize");
            stage.close();
            listener.onCertificateAccepted(request, certificate);
        });
        btnReject.setOnAction(e -> {
            stage.close();
            listener.onCertificateRejected(request, certificate);
        });
    }

    @Override
    public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {

        new JFXPanel();
        Platform.setImplicitExit(false);
        Platform.runLater(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("de/mein/auth/service/register.fxml"));
            BorderPane root = null;
            try {
                root = loader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }
            RegisterHandlerFX controller = loader.getController();
            controller.setCertificate(certificate);
            controller.setMyCertificate(myCertificate);
            controller.setListener(listener);
            controller.setRequest(request);
            Scene scene = new Scene(root);
            scene.getStylesheets().add("de/mein/modena_dark.css");
            Stage stage = new Stage();
            stage.setTitle("UML");
            stage.setScene(scene);
            stage.show();
            controller.setStage(stage);
        });


    }

    @Override
    public void onRegistrationCompleted(Certificate partnerCertificate) {

    }

    @Override
    public void onRemoteRejected(Certificate partnerCertificate) {

    }

    @Override
    public void onLocallyRejected(Certificate partnerCertificate) {

    }

    @Override
    public void onRemoteAccepted(Certificate partnerCertificate) {

    }

    @Override
    public void onLocallyAccepted(Certificate partnerCertificate) {

    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
}

package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.tools.N;
import de.mein.sql.Hash;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Locale;
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
    private Label lblMyCert, lblMyHash;
    @FXML
    private Label lblCert, lblHash;

    private Certificate myCertificate, certificate;
    private IRegisterHandlerListener listener;
    private Stage stage;
    private MeinRequest request;
    private MeinAuthAdminFX meinAthuAdminFX;
    private Locale locale;
    private ResourceBundle resourceBundle;

    public RegisterHandlerFX setRequest(MeinRequest request) {
        this.request = request;
        return this;
    }

    public void setMyCertificate(Certificate myCertificate) {
        this.myCertificate = myCertificate;
        showCertificate(txtMyCert, lblMyHash, myCertificate);
        lblMyCert.setText("My Certificate (" + myCertificate.getName().v() + ")");
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
        showCertificate(txtCert, lblHash, certificate);
        lblCert.setText("Incoming Certificate (" + certificate.getName().v() + ")");
    }

    private void showCertificate(TextArea txtArea, Label lblHash, Certificate cert) {
        N.r(() -> {
            X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(cert.getCertificate().v());
            txtArea.setText(x509Certificate.toString());
            String hash = Hash.sha256(x509Certificate.getEncoded());
            lblHash.setText(hash);
        });
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
        XCBFix.runLater(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("de/mein/auth/service/register.fxml"));
            ResourceBundle resourceBundle = ResourceBundle.getBundle("de/mein/auth/register", locale);
            loader.setResources(resourceBundle);
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
            scene.getStylesheets().add(MeinAuthAdminFX.GLOBAL_STYLE_CSS);
            Stage stage = new Stage();
            stage.setTitle(resourceBundle.getString("title"));
            stage.setScene(scene);
            Image image = new Image(MeinAuthAdminFX.APP_ICON_RES);
            stage.getIcons().add(image);
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

    @Override
    public void setup(MeinAuthAdmin meinAuthAdmin) {
        if (meinAuthAdmin instanceof MeinAuthAdminFX) {
            this.meinAthuAdminFX = (MeinAuthAdminFX) meinAuthAdmin;
            this.locale = meinAthuAdminFX.getLocale();
            this.resourceBundle = meinAthuAdminFX.getResourceBundle();
        } else {
            Lok.error("admin is not FX");
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
}

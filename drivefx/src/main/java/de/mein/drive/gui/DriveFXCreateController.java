package de.mein.drive.gui;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.RemoteServiceChooserFX;
import de.mein.auth.gui.EmbeddedServiceSettingsFX;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveClientService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import org.jdeferred.Promise;

import java.io.File;

/**
 * Created by xor on 10/20/16.
 */
public class DriveFXCreateController extends EmbeddedServiceSettingsFX {

    @FXML
    private Button btnPath;

    @FXML
    private TextField txtName, txtPath;

    private DriveCreateController driveCreateController;

    @Override
    public void onPrimaryClicked() {
        N.r(() -> {
            String name = txtName.getText().trim();
            Boolean isServer = this.isServerSelected();
            String role = isServer ? DriveStrings.ROLE_SERVER : DriveStrings.ROLE_CLIENT;
            String path = txtPath.getText();
            if (isServer)
                driveCreateController.createDriveServerService(name, path, 0.1f, 30);
            else {
                Certificate certificate = this.getSelectedCertificate();
                ServiceJoinServiceType serviceJoinServiceType = this.getSelectedService();
                Promise<MeinDriveClientService, Exception, Void> promise = driveCreateController.createDriveClientService(name, path, certificate.getId().v(), serviceJoinServiceType.getUuid().v(), 0.1f, 30);
                promise.done(meinDriveClientService -> N.r(() -> {
                    meinDriveClientService.syncThisClient();
                }));
            }
        });
    }

    @Override
    public void init() {
        driveCreateController = new DriveCreateController(meinAuthService);
        btnPath.setOnAction(event -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Choose Storage Directory");
            File dir = directoryChooser.showDialog(stage);
            if (dir != null) {
                txtPath.setText(dir.getAbsolutePath());
            } else {
                txtPath.setText(null);
            }
        });

    }

    @Override
    public String getTitle() {
        return "Create a new Drive instance";
    }


    @Override
    public void onServiceSpotted(RemoteServiceChooserFX.FoundServices foundServices, Long certId, ServiceJoinServiceType service) {
        try {
            if (service.getType().v().equals(new DriveBootLoader().getName())) {
                Certificate certificate = meinAuthService.getCertificateManager().getCertificateById(certId);
                Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId);
                connected.done(mvp -> N.r(() -> {
                    Request promise = mvp.request(service.getUuid().v(), DriveStrings.INTENT_DRIVE_DETAILS, null);
                    promise.done(result -> N.r(() -> {
                        DriveDetails driveDetails = (DriveDetails) result;
                        if (driveDetails.getRole() != null && driveDetails.getRole().equals(DriveStrings.ROLE_SERVER)) {
                            //finally found one
                            foundServices.lockWrite();
                            foundServices.add(certificate, service);
                            foundServices.unlockWrite();
                        }
                    }));
                }));

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

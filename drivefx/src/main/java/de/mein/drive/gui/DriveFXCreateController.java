package de.mein.drive.gui;

import de.mein.Lok;
import de.mein.auth.data.EmptyPayload;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.gui.EmbeddedServiceSettingsFX;
import de.mein.auth.gui.XCBFix;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.DriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.BashToolsUnix;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;

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
                driveCreateController.createDriveServerService(name, AFile.instance(path), 0.1f, 30);
            else {
                Certificate certificate = this.getSelectedCertificate();
                ServiceJoinServiceType serviceJoinServiceType = this.getSelectedService();
                driveCreateController.createDriveClientService(name, AFile.instance(path), certificate.getId().v(), serviceJoinServiceType.getUuid().v(), 0.1f, 30);
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
                // check if inotify limit is suffiently high on LINUX
                if (!BashTools.isWindows) {
                    N.thread(() -> {
                        BashToolsUnix bashToolsUnix = (BashToolsUnix) BashTools.getInstance();
                        final Long inotifyLimit = bashToolsUnix.getInotifyLimit();
                        final BashToolsUnix.SubDirCount subDirCount = bashToolsUnix.countSubDirs(dir);
                        // I think a 30% safety margin should be sufficient
                        final Long treshold = Double.valueOf(subDirCount.getCounted() * 1.33).longValue();
                        if (subDirCount.getCompleted()) {
                            if (treshold > inotifyLimit) {
                                // ask the user to increase inotify limit
                                Lok.error("inotify limit insufficient!");
                                Lok.error("current: "+inotifyLimit);
                                Lok.error("required: "+subDirCount.getCounted()+", recommended: "+treshold);
                                XCBFix.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("Inotify Limit too low");
                                    alert.setHeaderText("required: " + treshold);
                                    alert.setContentText("current: " + inotifyLimit);
                                    alert.showAndWait();
                                });
                            }
                        } else {
                            Lok.error("inotify limit probably insufficient!");
                            Lok.error("current: "+inotifyLimit);
                            Lok.error("required: "+subDirCount.getCounted()+", recommended: "+treshold);
                            XCBFix.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Inotify Limit proably too low");
                                alert.setHeaderText("subdirs found so far: "+subDirCount.getCounted());
                                alert.setContentText("current: "+inotifyLimit);
                                alert.showAndWait();
                            });
                        }
                    });

                }
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
    public void onServiceSpotted(NetworkEnvironment.FoundServices foundServices, Long certId, ServiceJoinServiceType service) {
        try {
            if (service.getType().v().equals(new DriveBootloader().getName())) {
                Certificate certificate = meinAuthService.getCertificateManager().getCertificateById(certId);
                Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId);
                connected.done(mvp -> N.r(() -> {
                    Request promise = mvp.request(service.getUuid().v(), new EmptyPayload(DriveStrings.INTENT_DRIVE_DETAILS));
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

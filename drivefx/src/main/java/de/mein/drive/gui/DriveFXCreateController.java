package de.mein.drive.gui;

import de.mein.MeinRunnable;
import de.mein.auth.data.EmptyPayload;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.gui.EmbeddedServiceSettingsFX;
import de.mein.auth.gui.XCBFix;
import de.mein.auth.service.MeinAuthAdminFX;
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
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import org.jdeferred.Promise;

import java.io.File;

/**
 * Created by xor on 10/20/16.
 */
public class DriveFXCreateController extends EmbeddedServiceSettingsFX {
    private final String DEFAULT_PRIMARY_BTN_TEXT = "Create File Share";
    private boolean shareWasChecked = false;
    @FXML
    private Text lblHints;
    @FXML
    private CheckBox cbIgnoreSymLinks;

    @FXML
    private Button btnPath;

    @FXML
    private TextField txtName, txtPath;

    private DriveCreateController driveCreateController;
    private MeinAuthAdminFX meinAuthAdminFX;

    private void checkShare() {
        File dir = new File(txtPath.getText());
        if (dir.exists()) {

            XCBFix.runLater(() -> {
                meinAuthAdminFX.setPrimaryButtonText("checking share directory...");
            });
            MeinRunnable runnable = new MeinRunnable() {

                @Override
                public String getRunnableName() {
                    return "counting dirs: " + dir.getAbsolutePath();
                }

                @Override
                public void run() {
                    N.r(() -> {

                        BashToolsUnix bashToolsUnix = (BashToolsUnix) BashTools.getInstance();
                        BashToolsUnix.ShareFolderProperties props = bashToolsUnix.getShareFolderPropeties(dir);
                        Long inotifyLimit = bashToolsUnix.getInotifyLimit();
                        Long proposedInotifyThreshold = Double.valueOf(props.getCounted() * 1.33).longValue();

                        XCBFix.runLater(() -> {
                            String hint = "Contains SymLinks: " + (props.getContainsSymLinks() ? "yes" : "no") + "\n";
                            if (props.getContainsSymLinks()) {
                                hint += "      SymLinks are not supported on Android.\n"
                                        + "      If you plan sharing this to an Android device tick 'ignore SymLinks' below.\n";
                            }
                            hint += "Subdirs: " + props.getCounted() + ", Inotify limit: " + inotifyLimit + " -> " + (inotifyLimit <= props.getCounted() ? "PROBLEM!\n" : "ok\n");
                            if (inotifyLimit <= props.getCounted()) {
                                hint += "      You set the user watch limit too low.\n" +
                                        "      Your should increase the limit to about: " + proposedInotifyThreshold;
                            }
                            shareWasChecked = true;
                            lblHints.setVisible(true);
                            lblHints.setText(hint);
                            meinAuthAdminFX.setPrimaryButtonText(DEFAULT_PRIMARY_BTN_TEXT);
                            meinAuthAdminFX.showPrimaryButtonOnly();

                        });
                    });
                }
            };
            meinAuthService.execute(runnable);
        }
    }

    @Override
    public boolean onPrimaryClicked() {
        File dir = new File(txtPath.getText());
        if (!dir.exists()) {

            return false;
        }
        if (!dir.isDirectory()) {

            return false;
        }
        if (!dir.canWrite()) {

            return false;
        }
        if (!BashTools.isWindows && !shareWasChecked) {
            checkShare();
            return false;
        }
        return N.result(() -> {
            final String name = txtName.getText().trim();
            final boolean isServer = this.isServerSelected();
            final String path = txtPath.getText();
            final boolean useSymLinks = !cbIgnoreSymLinks.isSelected();
            if (isServer)
                driveCreateController.createDriveServerService(name, AFile.instance(path), 0.1f, 30, useSymLinks);
            else {
                Certificate certificate = this.getSelectedCertificate();
                ServiceJoinServiceType serviceJoinServiceType = this.getSelectedService();
                driveCreateController.createDriveClientService(name, AFile.instance(path), certificate.getId().v(), serviceJoinServiceType.getUuid().v(), 0.1f, 30, useSymLinks);
            }
            return true;
        }, false);
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        super.configureParentGui(meinAuthAdminFX);
        if (BashTools.isWindows) {
            meinAuthAdminFX.setPrimaryButtonText(DEFAULT_PRIMARY_BTN_TEXT);
        } else {
            meinAuthAdminFX.setPrimaryButtonText(getString("choose.btn.checkDir"));
        }
        this.meinAuthAdminFX = meinAuthAdminFX;
    }

    @Override
    public void onRbServerSelected() {
        cbIgnoreSymLinks.selectedProperty().setValue(false);
        cbIgnoreSymLinks.disableProperty().setValue(false);
    }

    @Override
    public void onRbClientSelected() {
        cbIgnoreSymLinks.disableProperty().setValue(true);
    }

    @Override
    public void onServiceSelected(Certificate selectedCertificate, ServiceJoinServiceType selectedService) {
        if (selectedService != null && selectedService.getAdditionalServicePayload() != null) {
            DriveDetails driveDetails = (DriveDetails) selectedService.getAdditionalServicePayload();
            // server dictates whether to user symlinks or not
            cbIgnoreSymLinks.selectedProperty().setValue(!driveDetails.usesSymLinks());
        }
    }

    @Override
    public void init() {
        driveCreateController = new DriveCreateController(meinAuthService);
        btnPath.setOnAction(event -> {
            shareWasChecked = false;
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Choose Storage Directory");
            File dir = directoryChooser.showDialog(stage);
            if (dir != null) {
                txtPath.setText(dir.getAbsolutePath());
            } else {
                txtPath.setText(null);
            }
        });
        txtPath.textProperty().addListener(event -> {
            shareWasChecked = false;
            lblHints.setVisible(false);
        });

    }

    @Override
    public String getTitle() {
        return getString("create.title");
    }


    @Override
    public void onServiceSpotted(NetworkEnvironment.FoundServices foundServices, Long certId, ServiceJoinServiceType service) {
        try {
            if (service.getType().v().equals(new DriveBootloader().getName())) {
                if (service.getAdditionalServicePayload() != null) {
                    DriveDetails driveDetails = (DriveDetails) service.getAdditionalServicePayload();
                    if (driveDetails.getRole().equals(DriveStrings.ROLE_SERVER)) {
                        Certificate certificate = meinAuthService.getCertificateManager().getCertificateById(certId);
                        foundServices.lockWrite();
                        foundServices.add(certificate, service);
                        foundServices.unlockWrite();
                    }
                }
//                Certificate certificate = meinAuthService.getCertificateManager().getCertificateById(certId);
//                Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId);
//                connected.done(mvp -> N.r(() -> {
//                    Request promise = mvp.request(service.getUuid().v(), new EmptyPayload(DriveStrings.INTENT_DRIVE_DETAILS));
//                    promise.done(result -> N.r(() -> {
//                        DriveDetails driveDetails = (DriveDetails) result;
//                        if (driveDetails.getRole() != null && driveDetails.getRole().equals(DriveStrings.ROLE_SERVER)) {
//                            //finally found one
//                            foundServices.lockWrite();
//                            foundServices.add(certificate, service);
//                            foundServices.unlockWrite();
//                        }
//                    }));
//                }));

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

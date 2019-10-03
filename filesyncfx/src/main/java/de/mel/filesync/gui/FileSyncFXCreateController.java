package de.mel.filesync.gui;

import de.mel.MelRunnable;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.file.AFile;
import de.mel.auth.gui.EmbeddedServiceSettingsFX;
import de.mel.auth.gui.XCBFix;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.tools.N;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.BashToolsUnix;
import de.mel.filesync.data.FileSyncDetails;
import de.mel.filesync.data.FileSyncStrings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;

import java.io.File;

/**
 * Created by xor on 10/20/16.
 */
public class FileSyncFXCreateController extends EmbeddedServiceSettingsFX {
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

    private MelAuthAdminFX melAuthAdminFX;

    private void checkShare() {
        File dir = new File(txtPath.getText());
        if (dir.exists()) {

            XCBFix.runLater(() -> {
                melAuthAdminFX.setPrimaryButtonText("checking share directory...");
            });
            MelRunnable runnable = new MelRunnable() {

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
                            melAuthAdminFX.setPrimaryButtonText(DEFAULT_PRIMARY_BTN_TEXT);
                            melAuthAdminFX.showPrimaryButtonOnly();

                        });
                    });
                }
            };
            melAuthService.execute(runnable);
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
            return createInstance(name, isServer, path, useSymLinks);
        }, false);
    }

    protected boolean createInstance(final String name, final boolean isServer, final String path, final boolean useSymLinks) {
        return N.result(() -> {
            FileSyncCreateServiceHelper fileSyncCreateServiceHelper = new FileSyncCreateServiceHelper(melAuthService);
            if (isServer)
                fileSyncCreateServiceHelper.createServerService(name, AFile.instance(path), 0.1f, 30, useSymLinks);
            else {
                Certificate certificate = this.getSelectedCertificate();
                ServiceJoinServiceType serviceJoinServiceType = this.getSelectedService();
                fileSyncCreateServiceHelper.createClientService(name, AFile.instance(path), certificate.getId().v(), serviceJoinServiceType.getUuid().v(), 0.1f, 30, useSymLinks);
            }
            return true;
        }, false);
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        super.configureParentGui(melAuthAdminFX);
        if (BashTools.isWindows) {
            melAuthAdminFX.setPrimaryButtonText(DEFAULT_PRIMARY_BTN_TEXT);
        } else {
            melAuthAdminFX.setPrimaryButtonText(getString("choose.btn.checkDir"));
        }
        this.melAuthAdminFX = melAuthAdminFX;
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
            FileSyncDetails fileSyncDetails = (FileSyncDetails) selectedService.getAdditionalServicePayload();
            // server dictates whether to user symlinks or not
            cbIgnoreSymLinks.selectedProperty().setValue(!fileSyncDetails.usesSymLinks());
        }
    }

    @Override
    public void init() {
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
            if (service.getType().v().equals(new FileSyncBootloader().getName())) {
                if (service.getAdditionalServicePayload() != null) {
                    FileSyncDetails fileSyncDetails = (FileSyncDetails) service.getAdditionalServicePayload();
                    if (fileSyncDetails.getRole().equals(FileSyncStrings.ROLE_SERVER)) {
                        Certificate certificate = melAuthService.getCertificateManager().getCertificateById(certId);
                        foundServices.lockWrite();
                        foundServices.add(certificate, service);
                        foundServices.unlockWrite();
                    }
                }
//                Certificate certificate = melAuthService.getCertificateManager().getCertificateById(certId);
//                Promise<MelValidationProcess, Exception, Void> connected = melAuthService.connect(certId);
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

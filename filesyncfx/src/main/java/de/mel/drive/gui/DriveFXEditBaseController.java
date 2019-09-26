package de.mel.drive.gui;

import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.gui.ServiceSettingsFX;
import de.mel.drive.data.DriveSettings;
import de.mel.drive.service.MelDriveService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Created by xor on 10/27/16.
 */
public abstract class DriveFXEditBaseController extends ServiceSettingsFX<MelDriveService> {

    @FXML
    protected Label lblRole;
    @FXML
    protected TextField txtPath;
    protected DriveSettings driveSettings;

    @Override
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        super.feed(serviceJoinServiceType);
        this.driveSettings = service.getDriveSettings();
        lblRole.setText(driveSettings.getRole());
        txtPath.setText(driveSettings.getRootDirectory().getPath());
    }
}

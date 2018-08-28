package de.mein.drive.gui;

import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.ServiceSettingsFX;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.service.MeinDriveService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Created by xor on 10/27/16.
 */
public abstract class DriveFXEditBaseController extends ServiceSettingsFX<MeinDriveService> {

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

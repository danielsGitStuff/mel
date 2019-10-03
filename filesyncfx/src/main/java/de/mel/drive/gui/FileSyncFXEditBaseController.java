package de.mel.drive.gui;

import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.gui.ServiceSettingsFX;
import de.mel.drive.data.FileSyncSettings;
import de.mel.drive.service.MelFileSyncService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Created by xor on 10/27/16.
 */
public abstract class FileSyncFXEditBaseController extends ServiceSettingsFX<MelFileSyncService> {

    @FXML
    protected Label lblRole;
    @FXML
    protected TextField txtPath;
    protected FileSyncSettings fileSyncSettings;

    @Override
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        super.feed(serviceJoinServiceType);
        this.fileSyncSettings = service.getFileSyncSettings();
        lblRole.setText(fileSyncSettings.getRole());
        txtPath.setText(fileSyncSettings.getRootDirectory().getPath());
    }
}

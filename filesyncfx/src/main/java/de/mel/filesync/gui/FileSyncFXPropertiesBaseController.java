package de.mel.filesync.gui;

import de.mel.auth.data.MelServiceProperties;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.gui.ServicePropertiesFX;
import de.mel.auth.gui.ServiceSettingsFX;
import de.mel.auth.tools.HumanNumbers;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.service.MelFileSyncService;
import de.mel.filesync.service.sync.FileSyncServiceProperties;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Created by xor on 10/27/16.
 */
public class FileSyncFXPropertiesBaseController extends ServicePropertiesFX<MelFileSyncService<?>> {
    @FXML
    public Label lblSpaceFree;
    @FXML
    public Label lblSpaceTotal;
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

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return "Properties title";
    }

    @Override
    public void feed(MelServiceProperties<?> serviceProperties) {
        FileSyncServiceProperties properties = (FileSyncServiceProperties) serviceProperties;
        if (properties != null) {
            lblSpaceTotal.setText(HumanNumbers.toByteSize(properties.getSpaceTotalBytes()));
            lblSpaceFree.setText(HumanNumbers.toByteSize(properties.getSpaceFreeBytes()));
        }
    }
}

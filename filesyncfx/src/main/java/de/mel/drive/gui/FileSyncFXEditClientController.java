package de.mel.drive.gui;

import de.mel.Lok;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.drive.data.FileSyncSettings;
import de.mel.drive.service.MelFileSyncClientService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Created by xor on 10/26/16.
 */
public class FileSyncFXEditClientController extends FileSyncFXEditBaseController {

    protected MelFileSyncClientService melDriveService;

    @FXML
    private Button btnSync;

    @Override
    public boolean onPrimaryClicked() {
        applyName();
        return false;
    }

    @Override
    public void init() {
        Lok.debug("FileSyncFXEditClientController.init");
        btnSync.setOnAction(event -> {
            try {
                melDriveService.syncThisClient();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public String getTitle() {
        return "Edit File Sync instance";
    }

    public FileSyncFXEditClientController setDriveSettings(FileSyncSettings fileSyncSettings) {
        this.fileSyncSettings = fileSyncSettings;
        return this;
    }

    @Override
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        super.feed(serviceJoinServiceType);
        this.melDriveService = (MelFileSyncClientService) super.service;
    }
}

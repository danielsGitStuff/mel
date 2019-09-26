package de.mel.drive.gui;

import de.mel.Lok;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.drive.data.DriveSettings;
import de.mel.drive.service.MelDriveClientService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Created by xor on 10/26/16.
 */
public class DriveFXEditClientController extends DriveFXEditBaseController {

    protected MelDriveClientService melDriveService;

    @FXML
    private Button btnSync;

    @Override
    public boolean onPrimaryClicked() {
        applyName();
        return false;
    }

    @Override
    public void init() {
        Lok.debug("DriveFXEditClientController.init");
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
        return "Edit Drive instance";
    }

    public DriveFXEditClientController setDriveSettings(DriveSettings driveSettings) {
        this.driveSettings = driveSettings;
        return this;
    }

    @Override
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        super.feed(serviceJoinServiceType);
        this.melDriveService = (MelDriveClientService) super.service;
    }
}

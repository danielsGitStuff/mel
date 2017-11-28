package de.mein.drive.gui;

import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.service.MeinDriveClientService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Created by xor on 10/26/16.
 */
public class DriveFXEditClientController extends DriveFXEditBaseController {

    protected MeinDriveClientService meinDriveService;

    @FXML
    private Button btnSync;

    @Override
    public void onApplyClicked() {
        applyName();
    }

    @Override
    public void init() {
        System.out.println("DriveFXEditClientController.init");
        btnSync.setOnAction(event -> {
            try {
                meinDriveService.syncThisClient();
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
        this.meinDriveService = (MeinDriveClientService) super.service;
    }
}

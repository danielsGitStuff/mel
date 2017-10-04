package de.mein.drive.gui;

import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.ServiceSettingsFX;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.service.MeinDriveService;
import de.mein.sql.SqlQueriesException;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Created by xor on 10/27/16.
 */
public abstract class DriveFXEditBaseController extends ServiceSettingsFX {

    @FXML
    protected Label lblRole;
    @FXML
    protected TextField txtName;
    @FXML
    protected TextField txtPath;
    protected MeinDriveService meinDriveService;
    protected DriveSettings driveSettings;

    @Override
    public void onApplyClicked() {
        DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
        try {
            Service service = databaseManager.getServiceByUuid(meinDriveService.getUuid());
            service.setName(txtName.getText());
            databaseManager.updateService(service);

        } catch (SqlQueriesException e) {
            e.printStackTrace();
        } finally {
        }
    }

    @Override
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        this.meinDriveService = (MeinDriveService) meinAuthService.getMeinService(serviceJoinServiceType.getUuid().v());
        this.driveSettings = meinDriveService.getDriveSettings();
        lblRole.setText(driveSettings.getRole());
    }
}

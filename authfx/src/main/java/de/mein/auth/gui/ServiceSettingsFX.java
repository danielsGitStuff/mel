package de.mein.auth.gui;

import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinService;
import de.mein.sql.SqlQueriesException;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Created by xor on 10/26/16.
 */
public abstract class ServiceSettingsFX<T extends MeinService> extends AuthSettingsFX {

    /**
     * your extended class must have this
     */
    @FXML
    protected TextField txtName;

    protected T service;

    /**
     * feeds the currently selected Service (the version stored in database to {@link ServiceSettingsFX}
     *
     * @param serviceJoinServiceType
     */
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        this.service = (T) meinAuthService.getMeinService(serviceJoinServiceType.getUuid().v());
        txtName.setText(serviceJoinServiceType.getName().v());
    }

    protected void applyName() {
        DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
        try {
            Service service = databaseManager.getServiceByUuid(this.service.getUuid());
            service.setName(txtName.getText());
            databaseManager.updateService(service);
            meinAuthService.onServicesChanged();
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        } finally {
        }
    }
}

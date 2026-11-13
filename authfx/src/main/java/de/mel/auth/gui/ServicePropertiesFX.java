package de.mel.auth.gui;

import de.mel.auth.data.MelServiceProperties;
import de.mel.auth.data.access.DatabaseManager;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.service.MelService;
import de.mel.auth.tools.N;
import de.mel.sql.SqlQueriesException;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

/**
 * Created by xor on 10/26/16.
 */
public abstract class ServicePropertiesFX<T extends MelService> extends AuthSettingsFX {

    /**
     * your extended class must have this
     */
    @FXML
    protected TextField txtName;

    protected T service;

    /**
     * feeds the currently selected Service (the version stored in database to {@link ServicePropertiesFX}
     *
     * @param serviceJoinServiceType
     */
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        this.service = (T) melAuthService.getMelService(serviceJoinServiceType.getUuid().v());
        txtName.setText(serviceJoinServiceType.getName().v());
    }

    protected void applyName() {
        DatabaseManager databaseManager = melAuthService.getDatabaseManager();
        try {
            Service service = databaseManager.getServiceByUuid(this.service.getUuid());
            service.setName(txtName.getText());
            databaseManager.updateService(service);
            melAuthService.onServicesChanged();
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        } finally {
        }
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText("Edit");
//        melAuthAdminFX.setSecondaryButtonText("Delete");
        melAuthAdminFX.showPrimaryButtonOnly();
    }

    @Override
    public boolean onPrimaryClicked(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.showServiceSettings(this.service);
        return false;
    }

    @Override
    public void onSecondaryClicked(MelAuthAdminFX melAuthAdminFX) {
        N.r(() -> service.onShutDown());
        N.r(() -> {
            Service dbService = melAuthService.getDatabaseManager().getServiceByUuid(service.getUuid());
            melAuthService.unregisterMelService(dbService.getUuid().v());
            melAuthService.getDatabaseManager().deleteService(dbService.getId().v());
        });

    }

    public void feed(MelServiceProperties<?> serviceProperties) {
        System.out.println("ServicePropertiesFX.feed");
    }
}

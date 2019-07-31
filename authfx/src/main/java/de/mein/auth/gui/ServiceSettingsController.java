package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.ResourceBundle;

public class ServiceSettingsController extends AuthSettingsFX {
    @FXML
    private Label lblName, lblState;

    @FXML
    private GridPane content;

    private ServiceSettingsFX contentController;


    @Override
    public boolean onPrimaryClicked() {
        return contentController.onPrimaryClicked();
    }

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return resources.getString("service.title");
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        contentController.configureParentGui(meinAuthAdminFX);
    }


    @Override
    public void setMeinAuthService(MeinAuthService meinAuthService) {
        super.setMeinAuthService(meinAuthService);
        contentController.setMeinAuthService(meinAuthService);
    }

    @Override
    public void onSecondaryClicked() {
        contentController.onSecondaryClicked();
    }

    public void setContent(Pane pane, ServiceSettingsFX contentController) {
        content.add(pane, 0, 0);
        this.contentController = contentController;
    }

    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        contentController.feed(serviceJoinServiceType);
        lblName.setText(serviceJoinServiceType.getName().v());
        // set status

        {
            String text = "no text";
            String styleClass = "";
            if (serviceJoinServiceType.getActive().v()) {
                MeinService meinService = meinAuthService.getMeinService(serviceJoinServiceType.getUuid().v());
                if (meinService == null) {
                    styleClass = "lbl-state-stopped";
                    text = getString("service.state.error");
                } else if (meinService.getBootLevel() == meinService.getReachedBootLevel()) {
                    styleClass = "lbl-state-running";
                    text = getString("service.state.running");
                } else {
                    switch (meinService.getReachedBootLevel()) {
                        case NONE:
                            Lok.error("boot level confusion!");
                        case SHORT:
                            styleClass = "lbl-state-booting";
                            text = getString("service.state.booting");
                            break;
                        default:
                            text = getString("service.state.error");
                            break;
                    }
                }
//                btnText = R.string.btnDeactivate;
            } else {
                text = getString("service.state.deactivated");
                styleClass = "lbl-state-stopped";
            }
            lblState.setText(text);
            lblState.getStyleClass().clear();
            lblState.getStyleClass().add(styleClass);
        }
    }
}

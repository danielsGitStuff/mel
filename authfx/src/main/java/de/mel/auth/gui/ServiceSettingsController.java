package de.mel.auth.gui;

import de.mel.Lok;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class ServiceSettingsController extends AuthSettingsFX {

    @FXML
    private Label lblName, lblState, lblType;

    @FXML
    private VBox content;

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
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        contentController.configureParentGui(melAuthAdminFX);
    }


    @Override
    public void setMelAuthService(MelAuthService melAuthService) {
        super.setMelAuthService(melAuthService);
        contentController.setMelAuthService(melAuthService);
    }

    @Override
    public void onSecondaryClicked() {
        contentController.onSecondaryClicked();
    }

    public void setContent(Pane pane, ServiceSettingsFX contentController) {
        content.getChildren().clear();
        content.getChildren().add(pane);
        this.contentController = contentController;
    }

    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        contentController.feed(serviceJoinServiceType);
        lblName.setText(serviceJoinServiceType.getName().v());
        lblType.setText(serviceJoinServiceType.getType().v());
        // set status

        {
            String text = "no text";
            String styleClass = "";
            if (serviceJoinServiceType.getActive().v()) {
                MelService melService = melAuthService.getMelService(serviceJoinServiceType.getUuid().v());
                if (melService == null) {
                    styleClass = "lbl-state-stopped";
                    text = getString("service.state.error");
                } else if (melService.getBootLevel() == melService.getReachedBootLevel()) {
                    styleClass = "lbl-state-running";
                    text = getString("service.state.running");
                } else {
                    switch (melService.getReachedBootLevel()) {
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

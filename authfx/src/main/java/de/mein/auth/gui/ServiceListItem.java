package de.mein.auth.gui;

import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthFX;
import de.mein.auth.service.MeinAuthService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by xor on 9/24/16.
 */
public class ServiceListItem extends ListCell<ServiceJoinServiceType> implements Initializable {
    private MeinAuthService meinAuthService;
    private ServiceJoinServiceType service;


    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    @Override
    protected void updateItem(ServiceJoinServiceType service, boolean empty) {
        super.updateItem(service, empty);
        if (!empty) {
            this.setText(service.getType().v() + "/" + service.getName().v());
            if (!service.isRunning()) {
                setStyle("-fx-background-color:rgba(0, 0, 0, 0.05)");
            }
        }
    }
}

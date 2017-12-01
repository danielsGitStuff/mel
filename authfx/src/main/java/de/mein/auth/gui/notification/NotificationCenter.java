package de.mein.auth.gui.notification;

import de.mein.auth.MeinNotification;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.service.MeinAuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.net.URL;
import java.util.ResourceBundle;

public class NotificationCenter {

    @FXML
    private ListView<MeinNotification> list;

    @FXML
    private Button btnClose;
    private MeinAuthAdminFX meinAuthAdminFX;
    private MeinAuthService meinAuthService;
    private Stage stage;

    public NotificationCenter() {
        System.out.println("NotificationCenter.NotificationCenter");
    }

    public void setMeinAuthAdminFX(MeinAuthAdminFX adminFx) {
        this.meinAuthAdminFX = adminFx;
        this.meinAuthService = meinAuthAdminFX.getMeinAuthService();
    }

    @FXML
    void initialize() {
        System.out.println("NotificationCenter.initialize");
        btnClose.setOnAction(event -> ((Stage) btnClose.getScene().getWindow()).close());
    }


    public void showNotifications() {
        Platform.runLater(() -> {
            list.setItems(meinAuthAdminFX.getNotifications());
            list.setCellFactory(param -> new NotificationListCell(meinAuthService));
            System.out.println("NotificationCenter.showNotifications");
        });
    }

    public void show() {
        stage.show();
    }
}

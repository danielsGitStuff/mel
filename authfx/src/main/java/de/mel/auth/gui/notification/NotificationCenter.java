package de.mel.auth.gui.notification;

import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.gui.XCBFix;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.service.MelAuthServiceImpl;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public class NotificationCenter {

    @FXML
    private ListView<MelNotification> list;

    @FXML
    private Button btnClose;
    private MelAuthAdminFX melAuthAdminFX;
    private MelAuthServiceImpl melAuthService;
    private Stage stage;

    public NotificationCenter() {
        Lok.debug("NotificationCenter.NotificationCenter");
    }

    public void setMelAuthAdminFX(MelAuthAdminFX adminFx) {
        this.melAuthAdminFX = adminFx;
        this.melAuthService = melAuthAdminFX.getMelAuthService();
    }

    @FXML
    void initialize() {
        Lok.debug("NotificationCenter.initialize");
        btnClose.setOnAction(event -> ((Stage) btnClose.getScene().getWindow()).close());
    }


    public void showNotifications() {
        XCBFix.runLater(() -> {
            list.setItems(melAuthAdminFX.getNotifications());
            list.setCellFactory(param -> new NotificationListCell(melAuthService,melAuthAdminFX.getResourceBundle()));
            Lok.debug("NotificationCenter.showNotifications");
        });
    }

    public void show() {
        stage.show();
    }
}

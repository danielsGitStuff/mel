package de.mein.auth.gui.notification;

import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.gui.XCBFix;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.service.MeinAuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public class NotificationCenter {

    @FXML
    private ListView<MeinNotification> list;

    @FXML
    private Button btnClose;
    private MeinAuthAdminFX meinAuthAdminFX;
    private MeinAuthService meinAuthService;
    private Stage stage;

    public NotificationCenter() {
        Lok.debug("NotificationCenter.NotificationCenter");
    }

    public void setMeinAuthAdminFX(MeinAuthAdminFX adminFx) {
        this.meinAuthAdminFX = adminFx;
        this.meinAuthService = meinAuthAdminFX.getMeinAuthService();
    }

    @FXML
    void initialize() {
        Lok.debug("NotificationCenter.initialize");
        btnClose.setOnAction(event -> ((Stage) btnClose.getScene().getWindow()).close());
    }


    public void showNotifications() {
        XCBFix.runLater(() -> {
            list.setItems(meinAuthAdminFX.getNotifications());
            list.setCellFactory(param -> new NotificationListCell(meinAuthService));
            Lok.debug("NotificationCenter.showNotifications");
        });
    }

    public void show() {
        stage.show();
    }
}

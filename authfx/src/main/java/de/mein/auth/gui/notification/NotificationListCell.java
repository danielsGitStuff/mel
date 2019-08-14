package de.mein.auth.gui.notification;

import de.mein.auth.MeinNotification;
import de.mein.auth.boot.BootLoaderFX;
import de.mein.auth.gui.Popup;
import de.mein.auth.gui.XCBFix;
import de.mein.auth.service.Bootloader;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.ResourceBundle;

public class NotificationListCell extends ListCell<MeinNotification> {
    private final MeinAuthService meinAuthService;
    private final ResourceBundle meinAuthResourceBundle;
    @FXML
    private Label lblText, lblTitle;
    @FXML
    private ImageView imgIcon;
    private MeinNotification notification;

    @FXML
    private Button btnOpen, btnIgnore;
    @FXML
    private HBox box;

    public NotificationListCell(MeinAuthService meinAuthService, ResourceBundle meinAuthResourceBundle) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/de/mein/auth/notificationlistcell.fxml"));
        loader.setResources(meinAuthResourceBundle);
        loader.setController(this);
        this.meinAuthService = meinAuthService;
        this.meinAuthResourceBundle = meinAuthResourceBundle;
        try {
            loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void updateItem(MeinNotification notification, boolean empty) {
        super.updateItem(notification, empty);
        this.notification = notification;
        XCBFix.runLater(() -> {
            if (empty || notification == null) {
                lblTitle.setText(null);
                lblText.setText(null);
                setGraphic(null);
            } else {
                lblText.setText(meinAuthService.getCompleteNotificationText(notification));
                lblTitle.setText(notification.getTitle());
                btnOpen.setOnAction(event -> {
                    N.r(() -> {
                        String name = meinAuthService.getDatabaseManager().getServiceNameByServiceUuid(notification.getServiceUuid());
                        Bootloader bootloader = meinAuthService.getMeinBoot().getBootLoader(name);
                        IMeinService meinService = meinAuthService.getMeinService(notification.getServiceUuid());
                        if (bootloader instanceof BootLoaderFX) {
                            BootLoaderFX bootLoaderFX = (BootLoaderFX) bootloader;
                            String containingPath = bootLoaderFX.getPopupFXML(meinService, notification);
                            Popup popup = new Popup(meinAuthService, notification, containingPath, meinAuthResourceBundle);
                        }
                    });
                });
                btnIgnore.setOnAction(event -> {
                    if (notification.isUserCancelable())
                        notification.cancel();
                });
                try {
                    String name = meinAuthService.getDatabaseManager().getServiceNameByServiceUuid(notification.getServiceUuid());
                    Bootloader bootLoader = meinAuthService.getMeinBoot().getBootLoader(name);
                    if (bootLoader instanceof BootLoaderFX) {
                        BootLoaderFX bootLoaderFX = (BootLoaderFX) bootLoader;
                        Image image = new Image(bootLoaderFX.getIconURL(), 40, 40, true, true);
                        imgIcon.setImage(image);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                setGraphic(box);
            }
        });

    }
}

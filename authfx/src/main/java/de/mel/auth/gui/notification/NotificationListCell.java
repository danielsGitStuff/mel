package de.mel.auth.gui.notification;

import de.mel.auth.MelNotification;
import de.mel.auth.boot.BootLoaderFX;
import de.mel.auth.gui.Popup;
import de.mel.auth.gui.XCBFix;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.tools.N;
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

public class NotificationListCell extends ListCell<MelNotification> {
    private final MelAuthServiceImpl melAuthService;
    private final ResourceBundle melAuthResourceBundle;
    @FXML
    private Label lblText, lblTitle;
    @FXML
    private ImageView imgIcon;
    private MelNotification notification;

    @FXML
    private Button btnOpen, btnIgnore;
    @FXML
    private HBox box;

    public NotificationListCell(MelAuthServiceImpl melAuthService, ResourceBundle melAuthResourceBundle) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/de/mel/auth/notificationlistcell.fxml"));
        loader.setResources(melAuthResourceBundle);
        loader.setController(this);
        this.melAuthService = melAuthService;
        this.melAuthResourceBundle = melAuthResourceBundle;
        try {
            loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void updateItem(MelNotification notification, boolean empty) {
        super.updateItem(notification, empty);
        this.notification = notification;
        XCBFix.runLater(() -> {
            if (empty || notification == null) {
                lblTitle.setText(null);
                lblText.setText(null);
                setGraphic(null);
            } else {
                lblText.setText(melAuthService.getCompleteNotificationText(notification));
                lblTitle.setText(notification.getTitle());
                btnOpen.setOnAction(event -> {
                    N.r(() -> {
                        String name = melAuthService.getDatabaseManager().getServiceNameByServiceUuid(notification.getServiceUuid());
                        Bootloader bootloader = melAuthService.getMelBoot().getBootLoader(name);
                        IMelService melService = melAuthService.getMelService(notification.getServiceUuid());
                        if (bootloader instanceof BootLoaderFX) {
                            BootLoaderFX bootLoaderFX = (BootLoaderFX) bootloader;
                            String containingPath = bootLoaderFX.getPopupFXML(melService, notification);
                            Popup popup = new Popup(melAuthService, notification, containingPath, melAuthResourceBundle);
                        }
                    });
                });
                btnIgnore.setOnAction(event -> {
                    if (notification.isUserCancelable())
                        notification.cancel();
                });
                try {
                    String name = melAuthService.getDatabaseManager().getServiceNameByServiceUuid(notification.getServiceUuid());
                    Bootloader bootLoader = melAuthService.getMelBoot().getBootLoader(name);
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

package de.mein.auth.gui.notification;

import de.mein.auth.MeinNotification;
import de.mein.auth.boot.BootLoaderFX;
import de.mein.auth.gui.Popup;
import de.mein.auth.service.BootLoader;
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

public class NotificationListCell extends ListCell<MeinNotification> {
    private final MeinAuthService meinAuthService;
    @FXML
    private Label lblText, lblTitle;
    @FXML
    private ImageView imgIcon;
    private MeinNotification notification;

    @FXML
    private Button btnOpen, btnIgnore;
    @FXML
    private HBox box;

    public NotificationListCell(MeinAuthService meinAuthService) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/de/mein/auth/notificationlistcell.fxml"));
        loader.setController(this);
        this.meinAuthService = meinAuthService;
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
        if (empty || notification == null) {
            lblTitle.setText(null);
            lblText.setText(null);
            setGraphic(null);
        } else {
            lblText.setText(notification.getText());
            lblTitle.setText(notification.getTitle());
            btnOpen.setOnAction(event -> {
                N.r(() -> {
                    String name = meinAuthService.getDatabaseManager().getServiceNameByServiceUuid(notification.getServiceUuid());
                    BootLoader bootloader = meinAuthService.getMeinBoot().getBootLoader(name);
                    IMeinService meinService = meinAuthService.getMeinService(notification.getServiceUuid());
                    if (bootloader instanceof BootLoaderFX) {
                        BootLoaderFX bootLoaderFX = (BootLoaderFX) bootloader;
                        String containingPath = bootLoaderFX.getPopupFXML(meinService, notification);
                        Popup popup = new Popup(meinAuthService, notification, containingPath);
//                        loadPopup(containingPath).done(popupContentFX -> {
//                            popupContentFX.init(meinService, meinNotification);
//                        });
                    }
                });
            });
            try {
                String name = meinAuthService.getDatabaseManager().getServiceNameByServiceUuid(notification.getServiceUuid());
                BootLoader bootLoader = meinAuthService.getMeinBoot().getBootLoader(name);
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
    }
}

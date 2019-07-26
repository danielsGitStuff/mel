package de.mein.auth.gui;

import de.mein.auth.MeinNotification;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.ResourceBundle;

public class Popup {

    @FXML
    private GridPane container;
    @FXML
    private Button btnOk;

    public Popup(MeinAuthService meinAuthService, MeinNotification notification, String containingPath, ResourceBundle meinAuthResourceBundle) {
        N.r(() -> {
            //load the popup first
            FXMLLoader loader = new FXMLLoader(MeinAuthAdminFX.class.getClassLoader().getResource("de/mein/auth/popup.fxml"));
            loader.setResources(meinAuthResourceBundle);
            loader.setController(this);
            Parent root = null;
            root = loader.load();
            Scene scene = new Scene(root);
            Stage stage = MeinAuthAdminFX.createStage(scene);
            stage.setTitle("MeinAuthAdmin.Popup '" + meinAuthService.getName() + "'");
            //load the content we want to display
            FXMLLoader contentLoader = new FXMLLoader(getClass().getClassLoader().getResource(containingPath));
            Parent parent = contentLoader.load();
            PopupContentFX contentController = contentLoader.getController();
            container.add(parent, 0, 0);
            contentController.init(stage ,meinAuthService, notification);
            btnOk.setOnAction(event -> {
                String resultMessage = contentController.onOkCLicked();
                if (resultMessage == null) {
                    container.getChildren().clear();
                    //dereference
                    notification.cancel();
                    btnOk.setOnAction(event1 -> {
                    });
                    container.getScene().getWindow().hide();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR, resultMessage);
                    alert.showAndWait();
                }
            });
            //show it to the world
            stage.show();
        });
    }
}

package de.mel.auth.gui;

import de.mel.auth.MelNotification;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
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

    public Popup(MelAuthService melAuthService, MelNotification notification, String containingPath, ResourceBundle melAuthResourceBundle) {
        N.r(() -> {
            //load the popup first
            FXMLLoader loader = new FXMLLoader(MelAuthAdminFX.class.getClassLoader().getResource("de/mel/auth/popup.fxml"));
            loader.setResources(melAuthResourceBundle);
            loader.setController(this);
            Parent root = null;
            root = loader.load();
            Scene scene = new Scene(root);
            Stage stage = MelAuthAdminFX.createStage(scene);
            stage.setTitle("MelAuthAdmin.Popup '" + melAuthService.getName() + "'");
            //load the content we want to display
            FXMLLoader contentLoader = new FXMLLoader(getClass().getClassLoader().getResource(containingPath));
            Parent parent = contentLoader.load();
            PopupContentFX contentController = contentLoader.getController();
            container.add(parent, 0, 0);
            contentController.init(stage ,melAuthService, notification);
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

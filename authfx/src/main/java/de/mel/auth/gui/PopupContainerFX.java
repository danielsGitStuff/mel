package de.mel.auth.gui;

import de.mel.auth.tools.N;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

/**
 * Created by xor on 5/30/17.
 */
@Deprecated
public class PopupContainerFX {
    @FXML
    private GridPane container;
    @FXML
    private Button btnOk;

    public Promise<PopupContentFX, Void> load(String containingPath) {
        PromiseImpl<PopupContentFX, Void> deferred = PromiseImpl.withoutThrowingOnException();
        XCBFix.runLater(() -> N.r(() -> {
            FXMLLoader contentLoader = new FXMLLoader(PopupContainerFX.class.getClassLoader().getResource(containingPath));

            Parent root = contentLoader.load();
            PopupContentFX contentController = contentLoader.getController();
            container.add(root, 0, 0);
            btnOk.setOnAction(event -> {
                String resultMessage = contentController.onOkCLicked();
                if (resultMessage == null) {
                    container.getChildren().clear();
                    //dereference
                    btnOk.setOnAction(event1 -> {
                    });
                    container.getScene().getWindow().hide();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR, resultMessage);
                    alert.showAndWait();
                }
            });
            deferred.resolveOk(contentController);
        }));
        return deferred;
    }
}

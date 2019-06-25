package de.mein.auth;

import de.mein.Lok;
import de.mein.auth.gui.XCBFix;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class FxApp extends Application {
    private static FxApp instance;

    public static FxApp getInstance() {
        return instance;
    }

    private static Runnable runAfterStart;

    public static void setRunAfterStart(Runnable runAfterStart) {
        FxApp.runAfterStart = runAfterStart;
    }

    public static void start() {
        new Thread(() -> FxApp.launch()).start();
    }


    @Override
    public void start(Stage primaryStage) throws Exception {
        Lok.debug("fx app started");
        FxApp.instance = this;
        if (runAfterStart == null) {
            Lok.error("fx app started but you did not provide what to do afterwards");
        } else {
            Runnable r = runAfterStart;
            runAfterStart = null;
            r.run();
        }
    }

    public static void showErrorDialog(String title, String content) {
        showDialog(Alert.AlertType.ERROR, title, null, content);
    }

    public static void showInfoDialog(String title, String content) {
        showDialog(Alert.AlertType.INFORMATION, title, null, content);
    }

    public static void showDialog(Alert.AlertType type, String title, String header, String content) {
        XCBFix.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Update Info");
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.resizableProperty().setValue(true);
            alert.showAndWait();
        });
    }
}

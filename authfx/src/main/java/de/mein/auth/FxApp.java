package de.mein.auth;

import de.mein.Lok;
import javafx.application.Application;
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
}

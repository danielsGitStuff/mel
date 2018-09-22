package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.service.MeinAuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public class ProgressPopupContentFX extends PopupContentFX implements MeinNotification.MeinProgressListener {
    @FXML
    ProgressBar progressBar;

    @Override
    public String onOkCLicked() {
        return null;
    }

    @Override
    public void initImpl(Stage stage, MeinAuthService meinAuthService, MeinNotification notification) {
        notification.addProgressListener(this);
        if (!notification.isCanceled() || !notification.isFinished())
            showProgress(notification.getMax(), notification.getCurrent(), notification.isIndeterminate());
    }

    private void showProgress(int max, int current, boolean indeterminate) {
        double progress = ((double) current) / ((double) max);
        progressBar.setProgress(progress);
    }

    @Override
    public void onProgress(MeinNotification notification,int max, int current, boolean indeterminate) {
        showProgress(max, current, indeterminate);
    }

    @Override
    public void onCancel(MeinNotification notification) {
        Lok.debug("ProgressPopupContentFX.cancel()");
        Platform.runLater(() -> stage.close());
    }

    @Override
    public void onFinish(MeinNotification notification) {
        Lok.debug("ProgressPopupContentFX.finish()");
        progressBar.setProgress(1.0);
        stage.setTitle("Transfers done!");
    }
}

package de.mel.auth.gui;

import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.service.MelAuthServiceImpl;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public class ProgressPopupContentFX extends PopupContentFX implements MelNotification.MelProgressListener {
    @FXML
    ProgressBar progressBar;

    @Override
    public String onOkCLicked() {
        return null;
    }

    @Override
    public void initImpl(Stage stage, MelAuthServiceImpl melAuthService, MelNotification notification) {
        notification.addProgressListener(this);
        if (!notification.isCanceled() || !notification.isFinished())
            showProgress(notification.getMax(), notification.getCurrent(), notification.isIndeterminate());
    }

    private void showProgress(int max, int current, boolean indeterminate) {
        double progress = ((double) current) / ((double) max);
        progressBar.setProgress(progress);
    }

    @Override
    public void onProgress(MelNotification notification,int max, int current, boolean indeterminate) {
        showProgress(max, current, indeterminate);
    }

    @Override
    public void onCancel(MelNotification notification) {
        Lok.debug("ProgressPopupContentFX.cancel()");
        XCBFix.runLater(() -> stage.close());
    }

    @Override
    public void onFinish(MelNotification notification) {
        Lok.debug("ProgressPopupContentFX.finish()");
        progressBar.setProgress(1.0);
        stage.setTitle("Transfers done!");
    }
}

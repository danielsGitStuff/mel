package de.mein.auth.gui;

import de.mein.auth.MeinNotification;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;

public class ProgressPopupContentFX extends PopupContentFX implements MeinNotification.MeinProgressListener {
    @FXML
    ProgressBar progressBar;

    @Override
    public String onOkCLicked() {
        return null;
    }

    @Override
    public void initImpl(IMeinService meinService, MeinNotification notification) {
        notification.addProgressListener(this);
        if (!notification.isCanceled() || !notification.isFinished())
            showProgress(notification.getMax(), notification.getCurrent(), notification.isIndeterminate());
    }

    private void showProgress(int max, int current, boolean indeterminate) {
        double progress = ((double) current) / ((double) max);
        progressBar.setProgress(progress);
    }

    @Override
    public void onProgress(int max, int current, boolean indeterminate) {
        showProgress(max, current, indeterminate);
    }

    @Override
    public void cancel() {
        System.out.println("ProgressPopupContentFX.cancel()");
        Platform.runLater(() -> stage.close());
    }

    @Override
    public void finish() {
        System.out.println("ProgressPopupContentFX.finish()");
        progressBar.setProgress(1.0);
        stage.setTitle("Transfers done!");
    }
}

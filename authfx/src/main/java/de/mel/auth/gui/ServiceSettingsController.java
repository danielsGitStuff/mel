package de.mel.auth.gui;

import de.mel.auth.MelNotification;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.service.ServiceNotificationManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.ResourceBundle;

public class ServiceSettingsController extends AuthSettingsFX {

    @FXML
    private Label lblName, lblState, lblType, lblProgress;

    @FXML
    private ProgressBar progressBoot;

    @FXML
    private VBox content;

    private ServiceSettingsFX contentController;
    private ServiceJoinServiceType serviceJoinServiceType;
    private ServiceNotificationManager.ServiceStateListener stateListener;


    @Override
    public boolean onPrimaryClicked(MelAuthAdminFX melAuthAdminFX) {
        return contentController.onPrimaryClicked(melAuthAdminFX);
    }

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return resources.getString("service.settings.title");
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        contentController.configureParentGui(melAuthAdminFX);
    }


    @Override
    public void setMelAuthService(MelAuthServiceImpl melAuthService) {
        super.setMelAuthService(melAuthService);
        contentController.setMelAuthService(melAuthService);
    }

    @Override
    public void onSecondaryClicked(MelAuthAdminFX melAuthAdminFX) {
        contentController.onSecondaryClicked(melAuthAdminFX);
    }

    public void setContent(Pane pane, ServiceSettingsFX contentController) {
        content.getChildren().clear();
        content.getChildren().add(pane);
        this.contentController = contentController;
    }

    @Override
    public void onClose() {
        if (melAuthService != null && stateListener != null) {
            melAuthService.getNotificationManager().removeListener(stateListener);
            stateListener = null;
        }
    }

    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        this.serviceJoinServiceType = serviceJoinServiceType;
        contentController.feed(serviceJoinServiceType);
        lblName.setText(serviceJoinServiceType.getName().v());
        lblType.setText(serviceJoinServiceType.getType().v());
        if (melAuthService != null) {
            if (stateListener != null) {
                melAuthService.getNotificationManager().removeListener(stateListener);
            }
            String uuid = serviceJoinServiceType.getUuid().v();
            stateListener = changedUuid -> {
                if (changedUuid == null || changedUuid.equals(uuid)) {
                    XCBFix.runLater(this::updateState);
                }
            };
            melAuthService.getNotificationManager().addListener(stateListener);
        }
        updateState();
    }

    private void updateState() {
        if (melAuthService == null || serviceJoinServiceType == null) {
            return;
        }
        ServiceNotificationManager.ServiceState state = ServiceNotificationManager.getServiceState(melAuthService, serviceJoinServiceType);
        lblState.setText(stateText(resources, state));
        lblState.getStyleClass().setAll(stateStyleClass(state));

        List<MelNotification> active = melAuthService.getNotificationManager().getActiveNotifications(serviceJoinServiceType.getUuid().v());
        if (!active.isEmpty()) {
            MelNotification n = active.get(active.size() - 1);
            applyProgress(progressBoot, lblProgress, n);
        } else {
            hideProgress(progressBoot, lblProgress);
        }
    }

    public static String stateText(ResourceBundle resources, ServiceNotificationManager.ServiceState state) {
        switch (state) {
            case RUNNING:
                return resources.getString("service.state.running");
            case BOOTING:
                return resources.getString("service.state.booting");
            case DEACTIVATED:
                return resources.getString("service.state.deactivated");
            case ERROR:
            default:
                return resources.getString("service.state.error");
        }
    }

    public static String stateStyleClass(ServiceNotificationManager.ServiceState state) {
        switch (state) {
            case RUNNING:
                return "lbl-state-running";
            case BOOTING:
                return "lbl-state-booting";
            case DEACTIVATED:
            case ERROR:
            default:
                return "lbl-state-stopped";
        }
    }

    public static void applyProgress(ProgressBar bar, Label label, MelNotification notification) {
        if (bar != null) {
            if (notification.isIndeterminate() || notification.getMax() <= 0) {
                bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            } else {
                bar.setProgress((double) notification.getCurrent() / (double) notification.getMax());
            }
            bar.setVisible(true);
            bar.setManaged(true);
        }
        if (label != null) {
            String text = notification.getText() != null ? notification.getText() : notification.getTitle();
            label.setText(text != null ? text : "");
            boolean hasText = text != null && !text.isEmpty();
            label.setVisible(hasText);
            label.setManaged(hasText);
        }
    }

    public static void hideProgress(ProgressBar bar, Label label) {
        if (bar != null) {
            bar.setVisible(false);
            bar.setManaged(false);
        }
        if (label != null) {
            label.setVisible(false);
            label.setManaged(false);
        }
    }
}

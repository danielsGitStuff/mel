package de.mel.auth.gui;

import de.mel.auth.MelNotification;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.service.ServiceNotificationManager;
import de.mel.auth.tools.N;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

public class InfoController extends AuthSettingsFX {
    @FXML
    private Label lblStatus, lblName;
    @FXML
    private GridPane table, servicesTable;
    private int rowCount = 0;
    private ServiceNotificationManager.ServiceStateListener stateListener;

    @Override
    public boolean onPrimaryClicked(MelAuthAdminFX melAuthAdminFX) {

        return false;
    }

    @Override
    public void init() {
        N.r(() -> {
            Enumeration<NetworkInterface> iterNetwork;
            Enumeration<InetAddress> iterAddress;
            NetworkInterface network;
            InetAddress address;

            iterNetwork = NetworkInterface.getNetworkInterfaces();

            while (iterNetwork.hasMoreElements()) {
                network = iterNetwork.nextElement();

                if (!network.isUp() || network.isLoopback())
                    continue;

                iterAddress = network.getInetAddresses();

                while (iterAddress.hasMoreElements()) {
                    address = iterAddress.nextElement();

                    if (!address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isMulticastAddress()) {
                        Label netInterface = new Label(network.getName() + ": ");
                        netInterface.getStyleClass().add("lbl");
                        table.add(netInterface, 0, rowCount);

                        Label netAddress = new Label(address.getHostAddress());
                        netAddress.paddingProperty().setValue(new Insets(0, 0, 0, 5));
                        table.add(netAddress, 1, rowCount);
                        rowCount++;
                    }
                }
            }
        });
        N.r(() -> {
            if (melAuthService != null) {
                lblStatus.setText(getString("info.status.working"));
                lblStatus.getStyleClass().clear();
                lblStatus.getStyleClass().add("lbl-positive");
                lblName.setText(melAuthService.getName());
                if (stateListener != null) {
                    melAuthService.getNotificationManager().removeListener(stateListener);
                }
                stateListener = changedUuid -> XCBFix.runLater(this::refreshServices);
                melAuthService.getNotificationManager().addListener(stateListener);
                refreshServices();
            } else {
                lblStatus.setText(resources.getString("info.status.notWorking"));
                lblName.setText("-");
                lblStatus.getStyleClass().clear();
                lblStatus.getStyleClass().add("lbl-negative");
            }
        });
    }

    @Override
    public void onClose() {
        if (melAuthService != null && stateListener != null) {
            melAuthService.getNotificationManager().removeListener(stateListener);
            stateListener = null;
        }
    }

    private void refreshServices() {
        if (melAuthService == null || servicesTable == null) {
            return;
        }
        N.r(() -> {
            List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
            servicesTable.getChildren().clear();
            if (services.isEmpty()) {
                Label empty = new Label(resources.getString("info.services.none"));
                empty.getStyleClass().add("lbl");
                servicesTable.add(empty, 0, 0);
                return;
            }
            int row = 0;
            for (ServiceJoinServiceType service : services) {
                Label nameLabel = new Label(service.getName().v());
                nameLabel.getStyleClass().add("lbl");
                Label typeLabel = new Label(service.getType().v());
                typeLabel.getStyleClass().add("lbl");

                ServiceNotificationManager.ServiceState state = ServiceNotificationManager.getServiceState(melAuthService, service);
                Label stateLabel = new Label(ServiceSettingsController.stateText(resources, state));
                stateLabel.getStyleClass().setAll("lbl", ServiceSettingsController.stateStyleClass(state));

                servicesTable.add(nameLabel, 0, row);
                servicesTable.add(typeLabel, 1, row);
                servicesTable.add(stateLabel, 2, row);

                List<MelNotification> active = melAuthService.getNotificationManager().getActiveNotifications(service.getUuid().v());
                if (!active.isEmpty()) {
                    MelNotification n = active.get(active.size() - 1);
                    ProgressBar bar = new ProgressBar();
                    bar.setPrefWidth(100);
                    Label progressLabel = new Label();
                    progressLabel.getStyleClass().add("lbl");
                    ServiceSettingsController.applyProgress(bar, progressLabel, n);
                    HBox progressBox = new HBox(6, bar, progressLabel);
                    servicesTable.add(progressBox, 3, row);
                }
                row++;
            }
        });
    }

    @Override
    public String getTitle() {
        return resources.getString("info.title");
//        return "Info goes here";
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.hideBottomButtons();
    }

}

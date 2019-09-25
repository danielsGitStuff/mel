package de.mel.auth.gui;

import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.tools.N;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.ResourceBundle;

public class InfoController extends AuthSettingsFX {
    @FXML
    private Label lblStatus, lblName;
    @FXML
    private GridPane table;
    private int rowCount = 0;

    @Override
    public boolean onPrimaryClicked() {

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
            } else {
                lblStatus.setText(resources.getString("info.status.notWorking"));
                lblName.setText("-");
                lblStatus.getStyleClass().clear();
                lblStatus.getStyleClass().add("lbl-negative");
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

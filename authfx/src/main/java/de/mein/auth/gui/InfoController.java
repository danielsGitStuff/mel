package de.mein.auth.gui;

import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.tools.N;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class InfoController extends AuthSettingsFX {
    @FXML
    private Label lblStatus, lblName;
    @FXML
    private GridPane table;
    private int rowCount = 0;

    @Override
    public void onApplyClicked() {

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
                        netInterface.getStyleClass().add("prop");
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
            if (meinAuthService != null) {
                lblStatus.setText("working!");
                lblName.setText(meinAuthService.getName());
            } else {
                lblStatus.setText("not working!");
                lblName.setText("-");
            }
        });
    }

    @Override
    public String getTitle() {
        return "Info goes here";
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.hideBottomButtons();
    }
}

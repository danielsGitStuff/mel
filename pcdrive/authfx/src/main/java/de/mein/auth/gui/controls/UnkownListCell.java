package de.mein.auth.gui.controls;

import de.mein.auth.data.NetworkEnvironment;
import javafx.scene.control.ListCell;

/**
 * Created by xor on 10/25/16.
 */
public class UnkownListCell extends ListCell<NetworkEnvironment.UnknownAuthInstance> {
    @Override
    protected void updateItem(NetworkEnvironment.UnknownAuthInstance authInstance, boolean empty) {
        super.updateItem(authInstance, empty);
        if (!empty) {
            setText(authInstance.getAddress() + ":" + authInstance.getPort() + "/" + authInstance.getPortCert());
        }
    }
}

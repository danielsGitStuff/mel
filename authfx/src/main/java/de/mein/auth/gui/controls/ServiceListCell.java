package de.mein.auth.gui.controls;

import de.mein.auth.data.db.ServiceJoinServiceType;
import javafx.scene.control.ListCell;

/**
 * Created by xor on 10/25/16.
 */
public class ServiceListCell extends ListCell<ServiceJoinServiceType> {
    @Override
    protected void updateItem(ServiceJoinServiceType service, boolean empty) {
        super.updateItem(service, empty);
        if (!empty) {
            setText(service.getName().v());
        }
    }
}

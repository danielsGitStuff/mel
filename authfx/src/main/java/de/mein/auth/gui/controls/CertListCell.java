package de.mein.auth.gui.controls;

import de.mein.auth.data.db.Certificate;
import javafx.scene.control.ListCell;

/**
 * Created by xor on 10/25/16.
 */
public class CertListCell extends ListCell<Certificate> {
    @Override
    protected void updateItem(Certificate certificate, boolean empty) {
        super.updateItem(certificate, empty);
        if (!empty) {
            setText(certificate.getName().v());
        }
    }
}

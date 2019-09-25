package de.mel.auth.gui.controls;

import de.mel.auth.data.db.Certificate;
import javafx.scene.control.ListCell;

/**
 * Created by xor on 10/25/16.
 */
public class CertListCell extends ListCell<Certificate> {
    @Override
    protected void updateItem(Certificate certificate, boolean empty) {
        super.updateItem(certificate, empty);
        if (!empty) {
            if (certificate != null)
                setText(certificate.getName().v());
            else
                setText("cert is null");
        }
    }
}

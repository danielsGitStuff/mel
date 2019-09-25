package de.mel.auth;

import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

/**
 * Created by xor on 6/26/16.
 */
public abstract class TableThingy implements Callback<TableColumn.CellDataFeatures<ServiceJoinServiceType, String>, ObservableValue<String>> {
    private Certificate certificate;

    public TableThingy(Certificate certificate) {
        this.certificate = certificate;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public TableThingy setCertificate(Certificate certificate) {
        this.certificate = certificate;
        return this;
    }
}

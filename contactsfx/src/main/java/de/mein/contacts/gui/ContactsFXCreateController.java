package de.mein.contacts.gui;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.EmbeddedServerServiceSettingsFX;
import de.mein.auth.gui.RemoteServiceChooserFX;
import de.mein.contacts.ContactsBootloader;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.sql.SQLException;

public class ContactsFXCreateController extends EmbeddedServerServiceSettingsFX {

    @FXML
    private TextField txtName;

    @Override
    public void onServiceSpotted(RemoteServiceChooserFX.FoundServices foundServices, Long certId, ServiceJoinServiceType service) {
        try {
            if (service.getType().equalsValue(ContactStrings.NAME)) {
                Certificate certificate = meinAuthService.getCertificateManager().getCertificateById(certId);
                foundServices.lockWrite();
                foundServices.add(certificate, service);
                foundServices.unlockWrite();
            }
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onApplyClicked() {
        try {
            String name = txtName.getText();
            ContactsBootloader bootloader = (ContactsBootloader) meinAuthService.getMeinBoot().getBootLoader(ContactStrings.NAME);
            ContactsSettings contactsSettings = new ContactsSettings();
            contactsSettings.setRole(isServerSelected()? ContactStrings.ROLE_SERVER : ContactStrings.ROLE_CLIENT);
            bootloader.createService(name, contactsSettings);
        } catch (IllegalAccessException | InstantiationException | SqlQueriesException | IOException | SQLException | JsonSerializationException | ClassNotFoundException | JsonDeserializationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return null;
    }
}

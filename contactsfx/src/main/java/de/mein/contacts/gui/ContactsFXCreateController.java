package de.mein.contacts.gui;

import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.EmbeddedServiceSettingsFX;
import de.mein.auth.service.BootException;
import de.mein.contacts.ContactsBootloader;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.sql.SqlQueriesException;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class ContactsFXCreateController extends EmbeddedServiceSettingsFX {

    @FXML
    private TextField txtName;

    @Override
    public void onServiceSpotted(NetworkEnvironment.FoundServices foundServices, Long certId, ServiceJoinServiceType service) {
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
    public boolean onPrimaryClicked() {
        try {
            String name = txtName.getText();
            ContactsBootloader bootloader =(ContactsBootloader)  meinAuthService.getMeinBoot().getBootLoader(ContactStrings.NAME);
            ContactsSettings contactsSettings = new ContactsSettings();
            contactsSettings.setRole(isServerSelected()? ContactStrings.ROLE_SERVER : ContactStrings.ROLE_CLIENT);
            bootloader.createService(name, contactsSettings);
        } catch (BootException | IllegalAccessException | SqlQueriesException | InstantiationException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void init() {

    }

    @Override
    public String getTitle() {
        return getString("create.title");
    }
}

package de.mel.contacts.gui;

import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.gui.EmbeddedServiceSettingsFX;
import de.mel.auth.service.BootException;
import de.mel.contacts.ContactsBootloader;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.data.ContactsSettings;
import de.mel.sql.SqlQueriesException;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class ContactsFXCreateController extends EmbeddedServiceSettingsFX {

    @FXML
    private TextField txtName;

//    @Override
//    public void onServiceSpotted(NetworkEnvironment.FoundServices foundServices, Long certId, ServiceJoinServiceType service) {
//        try {
//            if (service.getType().equalsValue(ContactStrings.NAME)) {
//                Certificate certificate = melAuthService.getCertificateManager().getCertificateById(certId);
//                foundServices.lockWrite();
//                foundServices.add(certificate, service);
//                foundServices.unlockWrite();
//            }
//        } catch (SqlQueriesException e) {
//            e.printStackTrace();
//        }
//    }

    @Override
    public boolean onPrimaryClicked() {
        try {
            String name = txtName.getText();
            ContactsBootloader bootloader =(ContactsBootloader)  melAuthService.getMelBoot().getBootLoader(ContactStrings.NAME);
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

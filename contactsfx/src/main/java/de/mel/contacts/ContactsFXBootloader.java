package de.mel.contacts;

import de.mel.auth.MelNotification;
import de.mel.auth.boot.BootLoaderFX;
import de.mel.auth.service.IMelService;
import de.mel.contacts.service.ContactsServerService;
import de.mel.contacts.service.ContactsService;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsFXBootloader extends ContactsBootloader implements BootLoaderFX<ContactsService> {
    @Override
    public String getCreateFXML() {
        return "de/mel/contacts/create.embedded.fxml";
    }

    @Override
    public boolean embedCreateFXML() {
        return true;
    }

    @Override
    public String getEditFXML(ContactsService melService) {
        return (melService instanceof ContactsServerService) ? "de/mel/contacts/editserver.fxml" : "de/mel/contacts/editclient.fxml";
    }

    @Override
    public String getPopupFXML(IMelService melService, MelNotification dataObject) {
        return null;
    }

    @Override
    public String getIconURL() {
        return "de/mel/contacts/contacts.png";
    }

    @Override
    public ResourceBundle getResourceBundle(Locale locale) {
        return ResourceBundle.getBundle("de/mel/contacts/strings", locale);
    }

//    public ContactsService createService(String name, String role) throws SqlQueriesException, InstantiationException, IllegalAccessException, JsonSerializationException, IOException, ClassNotFoundException, SQLException, JsonDeserializationException {
//        MelBoot melBoot = melAuthService.getMelBoot();
//        Service service = createService(name);
//        File serviceDir = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v());
//        serviceDir.mkdirs();
//        File jsonFile = new File(serviceDir, "contacts.settings.json");
//        ContactsSettings contactsSettings = new ContactsSettings().setRole(role);
//        contactsSettings.setJsonFile(jsonFile).save();
//        ContactsService contactsService = boot(melAuthService, service, contactsSettings);
//        return contactsService;
//    }
//
//    public ContactsService boot(MelAuthService melAuthService, Service service, ContactsSettings contactsSettings) throws SqlQueriesException, JsonDeserializationException, JsonSerializationException, IOException, SQLException, IllegalAccessException, ClassNotFoundException {
//        File workingDirectory = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v());
//        ContactsService contactsService = null;
//        if (contactsSettings.isServer()) {
//            contactsService = createServerInstance(melAuthService, workingDirectory, service.getTypeId().v(), service.getUuid().v(), contactsSettings);
//            melAuthService.registerMelService(contactsService);
//        } else {
//            contactsService = createClientInstance(melAuthService, workingDirectory, service.getTypeId().v(), service.getUuid().v(), contactsSettings);
//            melAuthService.registerMelService(contactsService);
//        }
//        return contactsService;
//    }
//
//    protected ContactsService createClientInstance(MelAuthService melAuthService, File workingDirectory, Long serviceTypeId, String serviceUuid, ContactsSettings settings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
//        return new ContactsClientService(melAuthService, workingDirectory, serviceTypeId, serviceUuid, settings);
//    }
//
//    protected ContactsService createServerInstance(MelAuthService melAuthService, File workingDirectory, Long serviceId, String serviceTypeId, ContactsSettings contactsSettings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
//        return new ContactsServerService(melAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings);
//    }
//
//    private Service createService(String name) throws SqlQueriesException {
//        ServiceType type = melAuthService.getDatabaseManager().getServiceTypeByName(new ContactsBootloader().getName());
//        Service service = melAuthService.getDatabaseManager().createService(type.getId().v(), name);
//        return service;
//    }
//
//    @Override
//    public String getName() {
//        return ContactStrings.NAME;
//    }
//
//    @Override
//    public String getDescription() {
//        return "synchronizes you contacts";
//    }
//
//    @Override
//    public Promise<Void, Exception, Void> boot(MelAuthService melAuthService, List<Service> services) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
//        for (Service service : services) {
//            File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v() + File.separator + "contacts.settings.json");
//            ContactsSettings contactsSettings = (ContactsSettings) JsonSettings.load(jsonFile);
//            boot(melAuthService, service, contactsSettings);
//        }
//        return null;
//    }
}

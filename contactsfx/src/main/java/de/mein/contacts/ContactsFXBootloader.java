package de.mein.contacts;

import de.mein.auth.MeinNotification;
import de.mein.auth.boot.BootLoaderFX;
import de.mein.auth.service.IMeinService;
import de.mein.contacts.service.ContactsServerService;
import de.mein.contacts.service.ContactsService;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsFXBootloader extends ContactsBootloader implements BootLoaderFX<ContactsService> {
    @Override
    public String getCreateFXML() {
        return "de/mein/contacts/create.embedded.fxml";
    }

    @Override
    public boolean embedCreateFXML() {
        return true;
    }

    @Override
    public String getEditFXML(ContactsService meinService) {
        return (meinService instanceof ContactsServerService) ? "de/mein/contacts/editserver.fxml" : "de/mein/contacts/editclient.fxml";
    }

    @Override
    public String getPopupFXML(IMeinService meinService, MeinNotification dataObject) {
        return null;
    }

//    public ContactsService createService(String name, String role) throws SqlQueriesException, InstantiationException, IllegalAccessException, JsonSerializationException, IOException, ClassNotFoundException, SQLException, JsonDeserializationException {
//        MeinBoot meinBoot = meinAuthService.getMeinBoot();
//        Service service = createService(name);
//        File serviceDir = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v());
//        serviceDir.mkdirs();
//        File jsonFile = new File(serviceDir, "contacts.settings.json");
//        ContactsSettings contactsSettings = new ContactsSettings().setRole(role);
//        contactsSettings.setJsonFile(jsonFile).save();
//        ContactsService contactsService = boot(meinAuthService, service, contactsSettings);
//        return contactsService;
//    }
//
//    public ContactsService boot(MeinAuthService meinAuthService, Service service, ContactsSettings contactsSettings) throws SqlQueriesException, JsonDeserializationException, JsonSerializationException, IOException, SQLException, IllegalAccessException, ClassNotFoundException {
//        File workingDirectory = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v());
//        ContactsService contactsService = null;
//        if (contactsSettings.isServer()) {
//            contactsService = createServerInstance(meinAuthService, workingDirectory, service.getTypeId().v(), service.getUuid().v(), contactsSettings);
//            meinAuthService.registerMeinService(contactsService);
//        } else {
//            contactsService = createClientInstance(meinAuthService, workingDirectory, service.getTypeId().v(), service.getUuid().v(), contactsSettings);
//            meinAuthService.registerMeinService(contactsService);
//        }
//        return contactsService;
//    }
//
//    protected ContactsService createClientInstance(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String serviceUuid, ContactsSettings settings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
//        return new ContactsClientService(meinAuthService, workingDirectory, serviceTypeId, serviceUuid, settings);
//    }
//
//    protected ContactsService createServerInstance(MeinAuthService meinAuthService, File workingDirectory, Long serviceId, String serviceTypeId, ContactsSettings contactsSettings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
//        return new ContactsServerService(meinAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings);
//    }
//
//    private Service createService(String name) throws SqlQueriesException {
//        ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeByName(new ContactsBootloader().getName());
//        Service service = meinAuthService.getDatabaseManager().createService(type.getId().v(), name);
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
//    public Promise<Void, Exception, Void> boot(MeinAuthService meinAuthService, List<Service> services) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
//        for (Service service : services) {
//            File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v() + File.separator + "contacts.settings.json");
//            ContactsSettings contactsSettings = (ContactsSettings) JsonSettings.load(jsonFile);
//            boot(meinAuthService, service, contactsSettings);
//        }
//        return null;
//    }
}

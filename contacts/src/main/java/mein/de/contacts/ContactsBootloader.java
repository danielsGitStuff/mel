package mein.de.contacts;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import de.mein.auth.data.JsonSettings;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.ContactsSettings;
import mein.de.contacts.data.ContactsStrings;
import mein.de.contacts.data.db.ContactsDatabaseManager;
import mein.de.contacts.service.ContactsClientService;
import mein.de.contacts.service.ContactsServerService;
import mein.de.contacts.service.ContactsService;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsBootloader extends BootLoader {

    public ContactsService createService(String name, String role) throws SqlQueriesException, InstantiationException, IllegalAccessException, JsonSerializationException, IOException, ClassNotFoundException, SQLException, JsonDeserializationException {
        MeinBoot meinBoot = meinAuthService.getMeinBoot();
        Service service = createService(name);
        File serviceDir = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v());
        serviceDir.mkdirs();
        File jsonFile = new File(serviceDir, "contacts.settings.json");
        ContactsSettings contactsSettings = new ContactsSettings().setRole(role);
        contactsSettings.setJsonFile(jsonFile).save();
        ContactsService contactsService = boot(meinAuthService, service, contactsSettings);
        return contactsService;
    }

    public ContactsService boot(MeinAuthService meinAuthService, Service service, ContactsSettings contactsSettings) throws SqlQueriesException, JsonDeserializationException, JsonSerializationException, IOException, SQLException, IllegalAccessException, ClassNotFoundException {
        File workingDirectory = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v());
        ContactsService contactsService = null;
        if (contactsSettings.isServer()) {
            contactsService = createServerInstance(meinAuthService, workingDirectory, service.getTypeId().v(), service.getUuid().v(), contactsSettings);
            meinAuthService.registerMeinService(contactsService);
        } else {
            contactsService = createClientInstance(meinAuthService, workingDirectory, service.getTypeId().v(), service.getUuid().v(), contactsSettings);
            meinAuthService.registerMeinService(contactsService);
        }
        return contactsService;
    }

    protected ContactsService createClientInstance(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String serviceUuid, ContactsSettings settings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        return new ContactsClientService(meinAuthService, workingDirectory, serviceTypeId, serviceUuid, settings);
    }

    protected ContactsService createServerInstance(MeinAuthService meinAuthService, File workingDirectory, Long serviceId, String serviceTypeId, ContactsSettings contactsSettings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        return new ContactsServerService(meinAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings);
    }

    private Service createService(String name) throws SqlQueriesException {
        ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeByName(new ContactsBootloader().getName());
        Service service = meinAuthService.getDatabaseManager().createService(type.getId().v(), name);
        return service;
    }

    @Override
    public String getName() {
        return "contacts";
    }

    @Override
    public String getDescription() {
        return "synchronizes you contacts";
    }

    @Override
    public Promise<Void, Exception, Void> boot(MeinAuthService meinAuthService, List<Service> services) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        for (Service service : services) {
            File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v() + File.separator + "contacts.settings.json");
            ContactsSettings contactsSettings = (ContactsSettings) JsonSettings.load(jsonFile);
            boot(meinAuthService, service, contactsSettings);
        }
        return null;
    }
}

package de.mein.contacts;

import de.mein.auth.data.ClientData;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.ServiceDetails;
import de.mein.contacts.service.ContactsClientService;
import de.mein.contacts.service.ContactsServerService;
import de.mein.contacts.service.ContactsService;

import org.jdeferred.Promise;

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

/**
 * Created by xor on 9/21/17.
 */

public class ContactsBootloader extends BootLoader {

    public ContactsService createService(String name, ContactsSettings contactsSettings) throws SqlQueriesException, InstantiationException, IllegalAccessException, JsonSerializationException, IOException, ClassNotFoundException, SQLException, JsonDeserializationException {
        MeinBoot meinBoot = meinAuthService.getMeinBoot();
        Service service = createService(name);
        File serviceDir = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v());
        serviceDir.mkdirs();
        File jsonFile = new File(serviceDir, "contacts.settings.json");
        contactsSettings.setJsonFile(jsonFile).save();
        ContactsService contactsService = boot(meinAuthService, service, contactsSettings);
        if (!contactsSettings.isServer()) {
            // tell server we are here. if it goes wrong: reverse everything
            WaitLock waitLock = new WaitLock().lock();
            N runner = new N(e -> {
                meinAuthService.unregisterMeinService(service.getId().v());
                N.r(() -> meinAuthService.getDatabaseManager().deleteService(service.getId().v()));
                System.out.println("ContactsBootloader.createService.service.deleted:something.failed");
                waitLock.unlock();
            });
            runner.runTry(() -> meinAuthService.connect(contactsSettings.getClientSettings().getServerCertId())
                    .done(result -> {
                        String serverServiceUuid = contactsSettings.getClientSettings().getServiceUuid();
                        String serviceUuid = service.getUuid().v();
                        runner.runTry(() -> {
                            result.request(serverServiceUuid, ContactStrings.INTENT_REG_AS_CLIENT, new ServiceDetails(serviceUuid))
                                    .done(result1 -> {
                                        waitLock.unlock();
                                    }).fail(result1 -> runner.abort());

                        });
                    }).fail(result -> {
                        runner.abort();
                    }));
            waitLock.lock();
        }
        return contactsService;
    }

    public ContactsService boot(MeinAuthService meinAuthService, Service service, ContactsSettings contactsSettings) throws SqlQueriesException, JsonDeserializationException, JsonSerializationException, IOException, SQLException, IllegalAccessException, ClassNotFoundException {
        File workingDirectory = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v());
        ContactsService contactsService = null;
        if (contactsSettings.isServer()) {
            contactsService = createServerInstance(meinAuthService, workingDirectory, service.getTypeId().v(), service.getUuid().v(), contactsSettings);
            meinAuthService.registerMeinService(contactsService);
        } else {
            //allow the server to communicate with us
            N.r(() -> meinAuthService.getDatabaseManager().grant(service.getId().v(), contactsSettings.getClientSettings().getServerCertId()));
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
        return ContactStrings.NAME;
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

package de.mein.android.contacts.service;

import android.provider.ContactsContract;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mein.android.contacts.data.AndroidContactSettings;
import de.mein.android.contacts.data.ConflictIntentExtra;
import de.mein.android.contacts.data.db.ContactName;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.CountdownLock;
import de.mein.auth.tools.N;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsClientSettings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.data.db.dao.ContactsDao;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.contacts.jobs.CommitJob;
import de.mein.contacts.jobs.ExamineJob;
import de.mein.contacts.jobs.QueryJob;
import de.mein.contacts.service.ContactsClientService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;


/**
 * Created by xor on 10/4/17.
 */

public class AndroidContactsClientService extends ContactsClientService {

    private final AndroidServiceMethods serviceMethods;
    private ContactsToAndroidExporter contactsToAndroidExporter;

    public AndroidContactsClientService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
        serviceMethods = new AndroidServiceMethods(databaseManager);
        AndroidContactSettings androidContactSettings = (AndroidContactSettings) settingsCfg.getPlatformContactSettings();
        if (androidContactSettings.getPersistToPhoneBook()) {
            contactsToAndroidExporter = new ContactsToAndroidExporter(databaseManager);
        }
        databaseManager.maintenance();
        addJob(new ExamineJob());
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        super.handleRequest(request);
    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {
        super.handleMessage(payload, partnerCertificate, intent);
    }

    @Override
    protected void workWork(Job job) throws Exception {
        if (job instanceof ExamineJob) {
            PhoneBook phoneBook = serviceMethods.examineContacts(settings.getClientSettings().getUncommitedHead());
            settings.getClientSettings().setUncommitedHead(phoneBook.getId().v());
            settings.save();
            PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
            if (masterPhoneBook == null || masterPhoneBook.getHash().notEqualsValue(phoneBook.getHash())) {
                commitPhoneBook(phoneBook.getId().v());
            } else {
                databaseManager.getPhoneBookDao().deletePhoneBook(phoneBook.getId().v());
            }
            waitLock.lock();
        }else if (job instanceof CommitJob) {
            commitPhoneBook(settings.getClientSettings().getUncommitedHead());
        } else {
            super.workWork(job);
        }
    }
    protected void commitPhoneBook(Long phoneBookId) throws InterruptedException, SqlQueriesException {
        CountdownLock waitLock = new CountdownLock(1).lock();
        ContactsClientSettings clientSettings = databaseManager.getSettings().getClientSettings();
        //phone book has no contacts attached yet
        PhoneBook deepPhoneBook = databaseManager.getPhoneBookDao().loadDeepPhoneBook(phoneBookId);
        meinAuthService.connect(clientSettings.getServerCertId()).done(meinValidationProcess -> N.r(() -> meinValidationProcess.request(clientSettings.getServiceUuid(), ContactStrings.INTENT_UPDATE, deepPhoneBook)
                .done(result -> N.r(() -> {
                    System.out.println("AndroidContactsClientService.workWork. update succeeded");
                    databaseManager.getSettings().setMasterPhoneBookId(deepPhoneBook.getId().v());
                    databaseManager.getSettings().getClientSettings().setUncommitedHead(null);
                    databaseManager.getSettings().save();
                    AndroidContactSettings androidContactSettings = (AndroidContactSettings) databaseManager.getSettings().getPlatformContactSettings();
                    if (androidContactSettings.getPersistToPhoneBook()){
                        contactsToAndroidExporter.export(deepPhoneBook.getId().v());
                    }
                    waitLock.unlock();
                })).fail(result -> N.r(() -> {
                    System.err.println(getClass().getSimpleName() + " updating server failed!");
                    QueryJob queryJob = new QueryJob();
                    queryJob.getPromise().done(receivedPhoneBookId -> N.r(() -> checkConflict(deepPhoneBook.getId().v(), receivedPhoneBookId)));
                    addJob(queryJob);
                    waitLock.unlock();
                })))).fail(result -> {
            System.err.println(getClass().getSimpleName() + " updating server failed!!");
            waitLock.unlock();
        });
    }



    private void checkConflict(Long localPhoneBookId, Long receivedPhoneBookId) throws SqlQueriesException, InstantiationException, IllegalAccessException, JsonSerializationException {
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        ContactsDao contactsDao = databaseManager.getContactsDao();
        PhoneBook flatLocalPhoneBook = phoneBookDao.loadFlatPhoneBook(localPhoneBookId);
        PhoneBook flatReceived = phoneBookDao.loadFlatPhoneBook(receivedPhoneBookId);
        if (flatLocalPhoneBook != null && flatLocalPhoneBook.getHash().notEqualsValue(flatReceived.getHash())) {
            Set<Long> deletedLocalContactIds = new HashSet<>();
            Map<Long, Long> conflictingContactIds = new HashMap<>();
            Set<Long> newReceivedContactIds = new HashSet<>();
            ISQLResource<Contact> localResource = contactsDao.contactsResource(flatLocalPhoneBook.getId().v());
            Contact localContact = localResource.getNext();
            while (localContact != null) {
                List<ContactName> names = contactsDao.getWrappedAppendices(localContact.getId().v(), ContactName.class);
                if (names.size() == 1) {
                    ContactName contactName = names.get(0);
                    Contact receivedContact = contactsDao.getContactByName(receivedPhoneBookId, contactName.getName(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
                    if (receivedContact == null) {
                        deletedLocalContactIds.add(localContact.getId().v());
                    } else {
                        receivedContact.getChecked().v(true);
                        contactsDao.updateChecked(receivedContact);
                        if (receivedContact.getHash().notEqualsValue(localContact.getHash())) {
                            conflictingContactIds.put(localContact.getId().v(), receivedContact.getId().v());
                        }
                    }
                } else if (names.size() > 0) {
                    System.err.println("AndroidContactsClientService.checkConflict.TOO:MANY:NAMES");
                }
                localContact = localResource.getNext();
            }
            ISQLResource<Contact> receivedResource = contactsDao.contactsResource(receivedPhoneBookId, false);
            Contact receivedContact = receivedResource.getNext();
            while (receivedContact != null) {
                newReceivedContactIds.add(receivedContact.getId().v());
                receivedContact = receivedResource.getNext();
            }

            ConflictIntentExtra conflict = new ConflictIntentExtra(localPhoneBookId, receivedPhoneBookId);
            MeinNotification notification = new MeinNotification(getUuid(), ContactStrings.Notifications.INTENTION_CONFLICT, "CONFLICT TITLE", "conflict text");
            notification.addSerializedExtra(ContactStrings.Notifications.INTENT_EXTRA_CONFLICT, conflict);
            meinAuthService.onNotificationFromService(this, notification);
//            // store in android contacts application
//            if (contactsToAndroidExporter != null) {
//                contactsToAndroidExporter.export(receivedPhoneBookId);
//            }
        }
    }
}

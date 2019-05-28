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

import de.mein.Lok;
import de.mein.android.contacts.data.AndroidContactSettings;
import de.mein.android.contacts.data.ConflictIntentExtra;
import de.mein.android.contacts.data.db.ContactName;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.EmptyPayload;
import de.mein.auth.data.ServicePayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsClientSettings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.NewVersionDetails;
import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.data.db.PhoneBookWrapper;
import de.mein.contacts.data.db.dao.ContactsDao;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.contacts.jobs.CommitJob;
import de.mein.contacts.jobs.ExamineJob;
import de.mein.contacts.jobs.QueryJob;
import de.mein.contacts.service.ContactsClientService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.ISQLResource;
import de.mein.sql.Pair;
import de.mein.sql.SqlQueriesException;


/**
 * Created by xor on 10/4/17.
 */

@SuppressWarnings("unchecked")
public class AndroidContactsClientService extends ContactsClientService {

    private final AndroidServiceMethods serviceMethods;
    private ContactsToAndroidExporter contactsToAndroidExporter;

    public AndroidContactsClientService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
        AndroidContactSettings androidContactSettings = (AndroidContactSettings) settingsCfg.getPlatformContactSettings();
        serviceMethods = new AndroidServiceMethods(this, databaseManager, androidContactSettings);
        serviceMethods.listenForContactsChanges();
        if (androidContactSettings.getPersistToPhoneBook()) {
            contactsToAndroidExporter = new ContactsToAndroidExporter(databaseManager);
        }
        databaseManager.maintenance();
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        super.handleRequest(request);
    }

    @Override
    public void handleMessage(ServicePayload payload, Certificate partnerCertificate) {
        if (payload.hasIntent(ContactStrings.INTENT_PROPAGATE_NEW_VERSION)) {
            try {
                NewVersionDetails newVersionDetails = (NewVersionDetails) payload;
                PhoneBook master = databaseManager.getFlatMasterPhoneBook();
                if (master == null
                        || master.getVersion().notEqualsValue(newVersionDetails.getVersion())) {
                    QueryJob queryJob = new QueryJob();
                    queryJob.getPromise().done(receivedPhoneBookId -> N.r(() -> {
                        AndroidContactSettings androidContactSettings = (AndroidContactSettings) settings.getPlatformContactSettings();
                        if (androidContactSettings.getPersistToPhoneBook()) {
                            contactsToAndroidExporter.export(receivedPhoneBookId);
                        }
                    }));
                    addJob(queryJob);
                }

            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }

        } else {
            super.handleMessage(payload, partnerCertificate);
        }
    }

    @Override
    public void addJob(Job job) {
        super.addJob(job);
    }

    @Override
    protected void workWork(Job job) throws Exception {
        if (job instanceof ExamineJob) {
            final Long lastReadId = settings.getClientSettings().getLastReadId();
            PhoneBook lastReadPhonebook = lastReadId == null ? null : databaseManager.getPhoneBookDao().loadFlatPhoneBook(lastReadId);
            PhoneBook phoneBook = serviceMethods.examineContacts(lastReadPhonebook == null ? null : lastReadPhonebook.getVersion().v());
            PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
            if (masterPhoneBook == null || masterPhoneBook.getHash().notEqualsValue(phoneBook.getHash())) {
                settings.getClientSettings().setLastReadId(phoneBook.getId().v());
                settings.save();
                commitPhoneBook2Server(phoneBook.getId().v());
            } else {
                databaseManager.getPhoneBookDao().deletePhoneBook(phoneBook.getId().v());
            }
            //waitLock.lock();
        } else if (job instanceof CommitJob) {
            CommitJob commitJob = (CommitJob) job;
            commitPhoneBook2Server(commitJob.getPhoneBookId());
        } else if (job instanceof QueryJob) {
            QueryJob queryJob = (QueryJob) job;
            query(queryJob);
        } else {
            super.workWork(job);
        }
    }

    private void query(QueryJob queryJob) throws InterruptedException, SqlQueriesException {
        final Long serverCertId = databaseManager.getSettings().getClientSettings().getServerCertId();
        final String serviceUuid = databaseManager.getSettings().getClientSettings().getServiceUuid();
        PhoneBookDao phonebookDao = databaseManager.getPhoneBookDao();
        Transaction transaction = T.lockingTransaction(phonebookDao);
        meinAuthService.connect(serverCertId).done(mvp -> transaction.run(() ->
                mvp.request(serviceUuid, new EmptyPayload(ContactStrings.INTENT_QUERY))
                        .done(result -> transaction.run(() -> {
                            Lok.debug("ContactsClientService.workWork.query.success");
                            PhoneBookWrapper wrapper = (PhoneBookWrapper) result;
                            PhoneBook receivedPhoneBook = wrapper.getPhoneBook();
                            PhoneBook master = databaseManager.getFlatMasterPhoneBook();
                            receivedPhoneBook.getOriginal().v(false);
                            databaseManager.getPhoneBookDao().insertDeep(receivedPhoneBook);
                            Long lastReadId = databaseManager.getSettings().getClientSettings().getLastReadId();
                            boolean conflictOccurred = checkConflict(lastReadId, receivedPhoneBook.getId().v());
                            if (!conflictOccurred) {
                                Lok.debug("AndroidContactsClientService.commitPhoneBook2Server");
                                updateLocalPhoneBook(transaction, receivedPhoneBook.getId().v());
                                databaseManager.getSettings().setMasterPhoneBookId(receivedPhoneBook.getId().v());
                                databaseManager.getSettings().save();
                                databaseManager.getPhoneBookDao().deletePhoneBook(master.getId().v());
                            }
                            transaction.end();
                        }))
                        .fail(result -> transaction.run(() -> {
                            System.err.println("ContactsClientService.workWork");
                            queryJob.getPromise().reject(null);
                            transaction.end();
                        }))))
                .fail(result -> transaction.end());

    }

    protected void commitPhoneBook2Server(Long phoneBookId) throws InterruptedException, SqlQueriesException {
        ContactsClientSettings clientSettings = databaseManager.getSettings().getClientSettings();
        //phone book has no contacts attached yet
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        Transaction transaction = T.lockingTransaction(phoneBookDao);
        PhoneBook deepPhoneBook = databaseManager.getPhoneBookDao().loadDeepPhoneBook(phoneBookId);
        PhoneBookWrapper phoneBookWrapper = new PhoneBookWrapper(deepPhoneBook);
        phoneBookWrapper.setIntent(ContactStrings.INTENT_UPDATE);
        transaction.run(() -> meinAuthService.connect(clientSettings.getServerCertId())
                .done(mvp -> transaction.run(() -> mvp.request(clientSettings.getServiceUuid(), phoneBookWrapper)
                        .done(result -> transaction.run(() -> {
                            Lok.debug("AndroidContactsClientService.workWork. update succeeded");
                            updateLocalPhoneBook(transaction, deepPhoneBook.getId().v());
                            transaction.end();
                        })).fail(result -> transaction.run(() -> {
                            Lok.debug("updating server failed.");
                            QueryJob queryJob = new QueryJob();
                            addJob(queryJob);
                            transaction.end();
                        }))))
                .fail(result -> transaction.end()));
    }

    @Override
    public void onShutDown() {
        serviceMethods.onShutDown();
        super.onShutDown();
    }

    private void updateLocalPhoneBook(Transaction transaction, Long newPhoneBookId) throws IllegalAccessException, IOException, JsonSerializationException {
        Lok.debug("AndroidContactsClientService.workWork. update succeeded");
        transaction.run(() -> {
            databaseManager.getSettings().setMasterPhoneBookId(newPhoneBookId);
            //databaseManager.getSettings().getClientSettings().setLastReadId(null);
            databaseManager.getSettings().save();
            if (newPhoneBookId == settings.getClientSettings().getLastReadId()) {
                PhoneBook newPhoneBook = databaseManager.getPhoneBookDao().loadFlatPhoneBook(newPhoneBookId);
                if (!newPhoneBook.getOriginal().v()) {
                    export(newPhoneBookId);
                }
            }
        });
    }

    /**
     * exports to local android phonebook if hash from last reading differs
     *
     * @param newPhoneBookId
     * @throws SqlQueriesException
     */
    private void export(Long newPhoneBookId) throws SqlQueriesException {
        Long lastReadId = settings.getClientSettings().getLastReadId();
        AndroidContactSettings androidContactSettings = (AndroidContactSettings) databaseManager.getSettings().getPlatformContactSettings();
        Pair<String> lastReadHash = databaseManager.getPhoneBookDao().loadFlatPhoneBook(lastReadId).getHash();
        Pair<String> newHash = databaseManager.getPhoneBookDao().loadFlatPhoneBook(newPhoneBookId).getHash();
        if (androidContactSettings.getPersistToPhoneBook() && lastReadHash.notEqualsValue(newHash)) {
            contactsToAndroidExporter.export(newPhoneBookId);
        }
    }

    /**
     * @param localPhoneBookId
     * @param receivedPhoneBookId
     * @return true if conflict occurred
     * @throws SqlQueriesException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws JsonSerializationException
     */
    private boolean checkConflict(Long localPhoneBookId, Long receivedPhoneBookId) throws SqlQueriesException, InstantiationException, IllegalAccessException, JsonSerializationException {
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
            return true;
        } else
            return false;
    }
}

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
import de.mein.auth.tools.WaitLock;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsClientSettings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.NewVersionDetails;
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
import de.mein.sql.Pair;
import de.mein.sql.SqlQueriesException;


/**
 * Created by xor on 10/4/17.
 */

public class AndroidContactsClientService extends ContactsClientService {

    private final AndroidServiceMethods serviceMethods;
    private ContactsToAndroidExporter contactsToAndroidExporter;

    public AndroidContactsClientService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
        serviceMethods = new AndroidServiceMethods(this, databaseManager);
        serviceMethods.listenForContactsChanges();
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
        if (intent != null && intent.equals(ContactStrings.INTENT_PROPAGATE_NEW_VERSION)) {
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
            super.handleMessage(payload, partnerCertificate, intent);
        }
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
                commitPhoneBook(phoneBook.getId().v());
            } else {
                databaseManager.getPhoneBookDao().deletePhoneBook(phoneBook.getId().v());
            }
            //waitLock.lock();
        } else if (job instanceof CommitJob) {
            CommitJob commitJob = (CommitJob) job;
            commitPhoneBook(commitJob.getPhoneBookId());
        } else if (job instanceof QueryJob) {
            QueryJob queryJob = (QueryJob) job;
            query(queryJob);
        } else {
            super.workWork(job);
        }
    }

    private void query(QueryJob queryJob) throws InterruptedException, SqlQueriesException {
        WaitLock waitLock = new WaitLock().lock();
        final Long serverCertId = databaseManager.getSettings().getClientSettings().getServerCertId();
        final String serviceUuid = databaseManager.getSettings().getClientSettings().getServiceUuid();
        meinAuthService.connect(serverCertId).done(mvp -> N.r(() -> {
            mvp.request(serviceUuid, ContactStrings.INTENT_QUERY, null).done(result -> N.r(() -> {
                System.out.println("ContactsClientService.workWork.query.success");
                PhoneBook receivedPhoneBook = (PhoneBook) result;
                PhoneBook master = databaseManager.getFlatMasterPhoneBook();
                databaseManager.getPhoneBookDao().insertDeep(receivedPhoneBook);
                databaseManager.getSettings().setMasterPhoneBookId(receivedPhoneBook.getId().v());
                databaseManager.getSettings().save();
                queryJob.getPromise().resolve(receivedPhoneBook.getId().v());
                waitLock.unlock();
            })).fail(result -> {
                System.err.println("ContactsClientService.workWork");
                queryJob.getPromise().reject(null);
                waitLock.unlock();
            }).always((state, resolved, rejected) -> waitLock.unlock());
        })).fail(result -> {
            System.err.println("ContactsClientService.workWork.query.fail");
            queryJob.getPromise().reject(null);
            waitLock.unlock();
        });
        waitLock.lock();
    }

    protected void commitPhoneBook(Long phoneBookId) throws InterruptedException, SqlQueriesException {
        CountdownLock waitLock = new CountdownLock(1).lock();
        ContactsClientSettings clientSettings = databaseManager.getSettings().getClientSettings();
        //phone book has no contacts attached yet
        PhoneBook deepPhoneBook = databaseManager.getPhoneBookDao().loadDeepPhoneBook(phoneBookId);
        meinAuthService.connect(clientSettings.getServerCertId()).done(meinValidationProcess -> N.r(() -> meinValidationProcess.request(clientSettings.getServiceUuid(), ContactStrings.INTENT_UPDATE, deepPhoneBook)
                .done(result -> N.r(() -> {
                    System.out.println("AndroidContactsClientService.workWork. update succeeded");
                    updateLocalPhoneBook(deepPhoneBook.getId().v());
                    //export(deepPhoneBook.getId().v());
                    waitLock.unlock();
                })).fail(result -> N.r(() -> {
                    System.err.println(getClass().getSimpleName() + " updating server failed!");
                    QueryJob queryJob = new QueryJob();
                    queryJob.getPromise().done(receivedPhoneBookId -> N.r(() -> {
                        Boolean conflictOccurred = checkConflict(deepPhoneBook.getId().v(), receivedPhoneBookId);
                        if (!conflictOccurred) {
                            System.out.println("AndroidContactsClientService.commitPhoneBook");
                            updateLocalPhoneBook(receivedPhoneBookId);
                            databaseManager.getPhoneBookDao().deletePhoneBook(phoneBookId);
                        }
                    }));
                    addJob(queryJob);
                    waitLock.unlock();
                })))).fail(result -> {
            System.err.println(getClass().getSimpleName() + " updating server failed!!");
            waitLock.unlock();
        });
    }

    @Override
    public void shutDown() {
        super.shutDown();
    }


    @Override
    public void onShutDown() {
        super.onShutDown();
    }

    private void updateLocalPhoneBook(Long newPhoneBookId) throws IllegalAccessException, IOException, JsonSerializationException {
        System.out.println("AndroidContactsClientService.workWork. update succeeded");
        databaseManager.getSettings().setMasterPhoneBookId(newPhoneBookId);
        //databaseManager.getSettings().getClientSettings().setLastReadId(null);
        databaseManager.getSettings().save();
        try {
            export(newPhoneBookId);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }

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

package de.mel.android.contacts.service;

import android.provider.ContactsContract;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mel.Lok;
import de.mel.android.contacts.data.AndroidContactSettings;
import de.mel.android.contacts.data.ConflictIntentExtra;
import de.mel.android.contacts.data.db.ContactName;
import de.mel.auth.MelNotification;
import de.mel.auth.data.EmptyPayload;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.Job;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.data.ContactsClientSettings;
import de.mel.contacts.data.ContactsSettings;
import de.mel.contacts.data.NewVersionDetails;
import de.mel.contacts.data.db.Contact;
import de.mel.contacts.data.db.PhoneBook;
import de.mel.contacts.data.db.PhoneBookWrapper;
import de.mel.contacts.data.db.dao.ContactsDao;
import de.mel.contacts.data.db.dao.PhoneBookDao;
import de.mel.contacts.jobs.CommitJob;
import de.mel.contacts.jobs.ExamineJob;
import de.mel.contacts.jobs.QueryJob;
import de.mel.contacts.service.ContactsClientService;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.sql.ISQLResource;
import de.mel.sql.Pair;
import de.mel.sql.SqlQueriesException;
import org.jdeferred.Promise;


/**
 * Created by xor on 10/4/17.
 */

@SuppressWarnings("unchecked")
public class AndroidContactsClientService extends ContactsClientService {

    private final AndroidServiceMethods serviceMethods;
    private ContactsToAndroidExporter contactsToAndroidExporter;

    public AndroidContactsClientService(MelAuthService melAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
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
                    queryJob.done(receivedPhoneBookId -> N.r(() -> {
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
        Warden warden = P.confine(phonebookDao);
        melAuthService.connect(serverCertId).done(mvp -> warden.run(() ->
                mvp.request(serviceUuid, new EmptyPayload(ContactStrings.INTENT_QUERY))
                        .done(result -> warden.run(() -> {
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
                                updateLocalPhoneBook(warden, receivedPhoneBook.getId().v());
                                databaseManager.getSettings().setMasterPhoneBookId(receivedPhoneBook.getId().v());
                                databaseManager.getSettings().save();
                                databaseManager.getPhoneBookDao().deletePhoneBook(master.getId().v());
                            }
                            warden.end();
                        }))
                        .fail(result -> warden.run(() -> {
                            System.err.println("ContactsClientService.workWork");
                            queryJob.reject(null);
                            warden.end();
                        }))))
                .fail(result -> warden.end());

    }

    protected void commitPhoneBook2Server(Long phoneBookId) throws InterruptedException, SqlQueriesException {
        ContactsClientSettings clientSettings = databaseManager.getSettings().getClientSettings();
        //phone book has no contacts attached yet
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        Warden warden = P.confine(phoneBookDao);
        PhoneBook deepPhoneBook = databaseManager.getPhoneBookDao().loadDeepPhoneBook(phoneBookId);
        PhoneBookWrapper phoneBookWrapper = new PhoneBookWrapper(deepPhoneBook);
        phoneBookWrapper.setIntent(ContactStrings.INTENT_UPDATE);
        warden.run(() -> melAuthService.connect(clientSettings.getServerCertId())
                .done(mvp -> warden.run(() -> mvp.request(clientSettings.getServiceUuid(), phoneBookWrapper)
                        .done(result -> warden.run(() -> {
                            Lok.debug("AndroidContactsClientService.workWork. update succeeded");
                            updateLocalPhoneBook(warden, deepPhoneBook.getId().v());
                            warden.end();
                        })).fail(result -> warden.run(() -> {
                            Lok.debug("updating server failed.");
                            QueryJob queryJob = new QueryJob();
                            addJob(queryJob);
                            warden.end();
                        }))))
                .fail(result -> warden.end()));
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        serviceMethods.onShutDown();
        super.onShutDown();
        return null;
    }

    private void updateLocalPhoneBook(Warden warden, Long newPhoneBookId) throws IllegalAccessException, IOException, JsonSerializationException {
        Lok.debug("AndroidContactsClientService.workWork. update succeeded");
        warden.run(() -> {
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
            MelNotification notification = new MelNotification(getUuid(), ContactStrings.Notifications.INTENTION_CONFLICT, "CONFLICT TITLE", "conflict text");
            notification.addSerializedExtra(ContactStrings.Notifications.INTENT_EXTRA_CONFLICT, conflict);
            melAuthService.onNotificationFromService(this, notification);
//            // store in android contacts application
//            if (contactsToAndroidExporter != null) {
//                contactsToAndroidExporter.export(receivedPhoneBookId);
//            }
            return true;
        } else
            return false;
    }
}

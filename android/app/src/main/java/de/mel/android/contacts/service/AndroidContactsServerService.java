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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import de.mel.Lok;
import de.mel.android.contacts.data.AndroidContactSettings;
import de.mel.android.contacts.data.ConflictIntentExtra;
import de.mel.android.contacts.data.db.ContactName;
import de.mel.auth.MelNotification;
import de.mel.auth.jobs.Job;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.data.ContactsSettings;
import de.mel.contacts.data.db.Contact;
import de.mel.contacts.data.db.ContactAppendix;
import de.mel.contacts.data.db.PhoneBook;
import de.mel.contacts.data.db.dao.ContactsDao;
import de.mel.contacts.data.db.dao.PhoneBookDao;
import de.mel.contacts.jobs.AnswerQueryJob;
import de.mel.contacts.jobs.ExamineJob;
import de.mel.contacts.jobs.UpdatePhoneBookJob;
import de.mel.contacts.service.ContactsServerService;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.ISQLResource;
import de.mel.sql.SqlQueriesException;
import org.jdeferred.Promise;


/**
 * Created by xor on 10/3/17.
 */

public class AndroidContactsServerService extends ContactsServerService {
    private final AndroidServiceMethods serviceMethods;
    private ContactsToAndroidExporter contactsToAndroidExporter;

    public AndroidContactsServerService(MelAuthService melAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
        AndroidContactSettings androidContactSettings = (AndroidContactSettings) settingsCfg.getPlatformContactSettings();
        serviceMethods = new AndroidServiceMethods(this, databaseManager, androidContactSettings);
        if (androidContactSettings.getPersistToPhoneBook()) {
            contactsToAndroidExporter = new ContactsToAndroidExporter(databaseManager);
            serviceMethods.listenForContactsChanges();
        }
        databaseManager.maintenance();
        // examine when booted
        addJob(new ExamineJob());
    }

    private void debug() {
        N.r(() -> {
            PhoneBook read = serviceMethods.examineContacts(null);
            read = databaseManager.getPhoneBookDao().loadDeepPhoneBook(read.getId().v());

            PhoneBook debugBook = new PhoneBook();
            debugBook.getCreated().v(12L);
            debugBook.getVersion().v(0L);
            Contact contact = new Contact();
            contact.getPhonebookId().v(debugBook.getId());
            ContactAppendix appendix = new ContactAppendix(contact);
            appendix.getMimeType().v(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
            appendix.setValue(0, "Adolf Bedolf");
            appendix.setValue(1, "Adolf")
                    .setValue(2, "Bedolf")
                    .setValue(9, "1")
                    .setValue(10, "0");
            appendix.getContactId().v(contact.getId());
            contact.addAppendix(appendix);
            contact.hash();
            debugBook.addContact(contact);
            debugBook.hash();
            databaseManager.getPhoneBookDao().insertDeep(debugBook);

            ConflictIntentExtra conflict = new ConflictIntentExtra(read.getId().v(), debugBook.getId().v());
            MelNotification notification = new MelNotification(getUuid(), ContactStrings.Notifications.INTENTION_CONFLICT, "CONFLICT TITLE", "conflict text");
            notification.addSerializedExtra(ContactStrings.Notifications.INTENT_EXTRA_CONFLICT, SerializableEntitySerializer.serialize(conflict));
            melAuthService.onNotificationFromService(this, notification);
        });
    }

    public void debugonConflictSolved(PhoneBook merged) {
        N.r(() -> {
            String oldeHash = merged.getHash().v();
            PhoneBook before = databaseManager.getPhoneBookDao().loadDeepPhoneBook(merged.getId().v());
            before.hash();
            contactsToAndroidExporter.export(before.getId().v());
            PhoneBook after = serviceMethods.examineContacts(0L);
            PhoneBook afterDeep = databaseManager.getPhoneBookDao().loadDeepPhoneBook(after.getId().v());
            afterDeep.hash();
            Lok.debug("AndroidContactsServerService.debugonConflictSolved");
        });

    }

    private int count = 0;

    @Override
    protected void workWork(Job job) throws Exception {
        Lok.debug("AndroidContactsServerService.workWork");
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        ContactsSettings settings = databaseManager.getSettings();
        if (job instanceof ExamineJob) {
            PhoneBook phoneBook = serviceMethods.examineContacts(null);
            PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
            if (masterPhoneBook == null || masterPhoneBook.getHash().notEqualsValue(phoneBook.getHash())) {
                if (masterPhoneBook == null) {
                    phoneBook.getVersion().v(1L);
                } else {
                    Long newVersion = masterPhoneBook.getVersion().v() + 1;
                    Lok.debug("AndroidContactsServerService: setting new version to: " + newVersion);
                    phoneBook.getVersion().v(newVersion);
                }
                phoneBookDao.updateFlat(phoneBook);
                settings.setMasterPhoneBookId(phoneBook.getId().v());
                settings.save();
                propagateNewVersion(phoneBook.getVersion().v());
                //todo debug
                //contactsToAndroidExporter.export(phoneBook.getId().v());
            } else {
                Lok.debug("AndroidContactsServerService: phonebook did not change, id=" + phoneBook.getId().v() + ", version=" + phoneBook.getVersion().v());
                phoneBookDao.deletePhoneBook(phoneBook.getId().v());
            }
//            //todo debug
//            PhoneBook debugBook = new PhoneBook();
//            debugBook.getCreated().v(12L);
//            debugBook.getVersion().v(phoneBook.getVersion());
//            Contact contact = new Contact();
//            contact.getPhonebookId().v(debugBook.getId());
//            ContactAppendix appendix = new ContactAppendix(contact);
//            appendix.getMimeType().v(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
//            appendix.setValue(0, "Adolf Bedolf");
//            appendix.getContactId().v(contact.getId());
//            contact.addAppendix(appendix);
//            contact.hash();
//            debugBook.addContact(contact);
//            debugBook.hash();
//            phoneBookDao.insertDeep(debugBook);
            //todo debug
            //debugCheckConflict(phoneBook.getId().v(),debugBook.getId().v());
//            PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
//            if (count < 1)
//                contactsToAndroidExporter.export(masterPhoneBook.getId().v());
//            count++;
        } else if (job instanceof AnswerQueryJob) {
            super.workWork(job);
        } else if (job instanceof UpdatePhoneBookJob) {
            job.done(result -> N.r(() -> {
                UpdatePhoneBookJob updatePhoneBookJob = (UpdatePhoneBookJob) job;
                contactsToAndroidExporter.export(updatePhoneBookJob.getPhoneBook().getId().v());
            })).fail(result -> N.r(() -> {
                Lok.debug("AndroidContactsServerService.workWork.update failed :(");
            }));
            super.workWork(job);
        } else {
            super.workWork(job);
        }
    }

    private void debugCheckConflict(Long localPhoneBookId, Long receivedPhoneBookId) throws SqlQueriesException, InstantiationException, IllegalAccessException, JsonSerializationException {
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
            notification.addSerializedExtra(ContactStrings.Notifications.INTENT_EXTRA_CONFLICT, SerializableEntitySerializer.serialize(conflict));
            melAuthService.onNotificationFromService(this, notification);
//            // store in android contacts application
//            if (contactsToAndroidExporter != null) {
//                contactsToAndroidExporter.export(receivedPhoneBookId);
//            }
        }
    }

    @Override
    public void onServiceRegistered() {
        super.onServiceRegistered();
    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        super.onShutDown();
        serviceMethods.onShutDown();
        return null;
    }
}

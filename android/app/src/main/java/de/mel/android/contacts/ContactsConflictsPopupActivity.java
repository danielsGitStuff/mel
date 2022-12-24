package de.mel.android.contacts;

import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.annotation.NonNull;
import android.view.MenuItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.ConflictsPopupActivity;
import de.mel.android.Notifier;
import de.mel.android.contacts.data.ConflictIntentExtra;
import de.mel.android.contacts.data.db.ContactName;
import de.mel.android.contacts.service.AndroidContactsClientService;
import de.mel.android.contacts.view.ContactsConflictListAdapter;
import de.mel.android.service.AndroidService;
import de.mel.auth.MelNotification;
import de.mel.auth.MelStrings;
import de.mel.auth.tools.N;
import de.mel.contacts.data.ContactJoinDummy;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.data.db.Contact;
import de.mel.contacts.data.db.ContactAppendix;
import de.mel.contacts.data.db.PhoneBook;
import de.mel.contacts.data.db.dao.ContactsDao;
import de.mel.contacts.data.db.dao.PhoneBookDao;
import de.mel.contacts.jobs.CommitJob;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.ISQLResource;
import de.mel.sql.SqlQueriesException;

/**
 * Created by xor on 10/18/17.
 */

public class ContactsConflictsPopupActivity extends ConflictsPopupActivity<AndroidContactsClientService> {
    private Long lastReadPhoneBookId, receivedPhoneBookId;
    private ContactsConflictListAdapter adapter;

    @Override
    protected int layout() {
        return R.layout.activity_conflict_popup;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        Bundle extras = getIntent().getExtras();
        String json = extras.getString(MelStrings.Notifications.EXTRA + ContactStrings.Notifications.INTENT_EXTRA_CONFLICT);
        PhoneBookDao phoneBookDao = service.getDatabaseManager().getPhoneBookDao();
        ContactsDao contactsDao = service.getDatabaseManager().getContactsDao();
        N.r(() -> {
            ConflictIntentExtra conflictIntentExtra = (ConflictIntentExtra) SerializableEntityDeserializer.deserialize(json);
            lastReadPhoneBookId = conflictIntentExtra.getLocalPhoneBookId();
            receivedPhoneBookId = conflictIntentExtra.getReceivedPhoneBookId();
            PhoneBook flatLastreadPhoneBook = phoneBookDao.loadFlatPhoneBook(lastReadPhoneBookId);
            //todo debug
            adapter = new ContactsConflictListAdapter(this, service, lastReadPhoneBookId, receivedPhoneBookId);
            listView.setAdapter(adapter);
            runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
            });
            btnOk.setOnClickListener(v -> N.r(() -> {

                PhoneBook receivedPB = phoneBookDao.loadFlatPhoneBook(receivedPhoneBookId);
                PhoneBook merged = service.getDatabaseManager().getPhoneBookDao().create(receivedPB.getVersion().v() + 1, false);
                List<ContactJoinDummy> contactDummies = adapter.getContactDummies();
                for (ContactJoinDummy dummy : contactDummies) {
                    if (dummy.getChoice() != null) {
                        Contact choice = contactsDao.getContactById(dummy.getChoice());
                        List<ContactAppendix> appendices = contactsDao.getAppendices(dummy.getChoice());
                        choice.setAppendices(appendices);
                        choice.getId().nul();
                        choice.getPhonebookId().v(merged.getId());
                        contactsDao.insert(choice);
                        merged.hashContact(choice);
                    }
                }
                Lok.debug("ContactsConflictsPopupActivity: Conflict resolved!");
                merged.digest();
                if (merged.getHash().equalsValue(flatLastreadPhoneBook.getHash()))
                    merged.getOriginal().v(true);
                phoneBookDao.updateFlat(merged);
                //service.debugonConflictSolved(merged);
                phoneBookDao.deletePhoneBook(receivedPhoneBookId);
                //phoneBookDao.deletePhoneBook(lastReadPhoneBookId);
                //service.getDatabaseManager().getSettings().getClientSettings().setLastReadId(merged.getId().v());
                Notifier.cancel(getIntent(), requestCode);
                service.addJob(new CommitJob(merged.getId().v()));
                finish();
            }));
        });
    }

    private void debugCheckConflict(Long localPhoneBookId, Long receivedPhoneBookId) throws SqlQueriesException, InstantiationException, IllegalAccessException, JsonSerializationException {
        PhoneBookDao phoneBookDao = service.getDatabaseManager().getPhoneBookDao();
        ContactsDao contactsDao = service.getDatabaseManager().getContactsDao();
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
            MelNotification notification = new MelNotification(service.getUuid(), ContactStrings.Notifications.INTENTION_CONFLICT, "CONFLICT TITLE", "conflict text");
            notification.addSerializedExtra(ContactStrings.Notifications.INTENT_EXTRA_CONFLICT, SerializableEntitySerializer.serialize(conflict));
            service.getMelAuthService().onNotificationFromService(service, notification);
//            // store in android contacts application
//            if (contactsToAndroidExporter != null) {
//                contactsToAndroidExporter.export(receivedPhoneBookId);
//            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}

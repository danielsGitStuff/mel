package de.mein.android.contacts.service;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mein.Lok;
import de.mein.android.Tools;
import de.mein.android.contacts.data.AndroidContactSettings;
import de.mein.auth.service.MeinService;
import de.mein.auth.tools.WatchDogTimer;
import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.ContactAppendix;
import de.mein.contacts.data.db.ContactsDatabaseManager;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.data.db.dao.ContactsDao;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.contacts.jobs.ExamineJob;
import de.mein.sql.Pair;
import de.mein.sql.SqlQueriesException;


/**
 * Created by xor on 10/4/17.
 */

public class AndroidServiceMethods {

    private final ContactsDatabaseManager databaseManager;
    private final MeinService service;
    private final AndroidContactSettings androidContactSettings;
    private ContentObserver observer;

    public AndroidServiceMethods(MeinService service, ContactsDatabaseManager databaseManager, AndroidContactSettings androidContactSettings) {
        this.databaseManager = databaseManager;
        this.service = service;
        this.androidContactSettings = androidContactSettings;
    }


    /**
     * @return flat {@link PhoneBook}
     * @throws SqlQueriesException
     */
    public PhoneBook examineContacts(Long lastUncommitedHeadVersion) throws SqlQueriesException {
        Lok.debug("starting examination");
        ContactsDao contactsDao = databaseManager.getContactsDao();
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        PhoneBook phoneBook;
        PhoneBook master = databaseManager.getFlatMasterPhoneBook();
        if (master == null)
            phoneBook = phoneBookDao.create(0L, true);
        else if (lastUncommitedHeadVersion != null) {
            Long max = lastUncommitedHeadVersion;
            if (master.getVersion().v() > max)
                max = master.getVersion().v();
            phoneBook = phoneBookDao.create(max + 1, true);
        } else
            phoneBook = phoneBookDao.create(master.getVersion().v() + 1, true);
        String[] projContact = new String[]{
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.NAME_RAW_CONTACT_ID,
                ContactsContract.Contacts.PHOTO_FILE_ID
        };

        ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
        Cursor contactCursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projContact, null, null, null);
        // Iterate every contact in the phone
        while (contactCursor.moveToNext()) {
            // read rawId first
            Contact contact = new Contact();
            final String contactId = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts._ID));
            final String photoFileId = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts.PHOTO_FILE_ID));
            contact.getAndroidId().v(contactCursor.getLong(contactCursor.getColumnIndex(ContactsContract.Contacts.NAME_RAW_CONTACT_ID)));
            String rawContactId = contact.getAndroidId().v().toString();
            readAppendices(contact, rawContactId);
            readPhoto(contact, contactId);
            contact.hash();
            contact.getPhonebookId().v(phoneBook.getId());
            contactsDao.insert(contact);
            phoneBook.hashContact(contact);
        }
        phoneBook.digest();
        phoneBookDao.updateFlat(phoneBook);
        contactCursor.close();
        return phoneBook;
    }

    private void readMimeTypes(PhoneBook phoneBook) {
        Cursor cursor = Tools.getApplicationContext().getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, null, null, null);
        List<Pair<String>> list = new ArrayList<>();
        if (cursor.moveToNext()) {
            for (Integer i = 0; i < cursor.getColumnCount(); i++) {
                Pair<String> pair = new Pair<>(String.class, cursor.getColumnName(i), cursor.getString(i));
                list.add(pair);
            }
            Lok.debug("AndroidServiceMethods.readMimeTypes");
        }
    }

    private void readAppendices(Contact contact, String rawContactId) {
        String selection = ContactsContract.Data.RAW_CONTACT_ID + " = ? and " + ContactsContract.Data.MIMETYPE + "<>?";
        DataTableCursorReader reader = DataTableCursorReader.query(ContactsContract.Data.CONTENT_URI, selection, new String[]{rawContactId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE}, null);
        while (reader.moveToNext()) {
            ContactAppendix appendix = reader.readDataColumns();
            if (appendix != null)
                contact.addAppendix(appendix);
        }
        reader.close();
    }

    private void readElse(Contact contact, String rawContactId) {
        String selection = ContactsContract.Data.RAW_CONTACT_ID + " = ? and " + ContactsContract.Data.MIMETYPE + "=?";
        DataTableCursorReader reader = DataTableCursorReader.query(ContactsContract.Data.CONTENT_URI, selection, new String[]{rawContactId, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE}, null);
    }

    public static void readPhoto(Contact contact, String contactId) {
        InputStream inputStream = ContactsContract.Contacts.openContactPhotoInputStream(Tools.getApplicationContext().getContentResolver(),
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.valueOf(contactId)), true);
        try {
            if (inputStream != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int bytesRead;
                byte[] data = new byte[16384];
                while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }
                buffer.flush();
                contact.getImage().v(buffer.toByteArray());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String read(Cursor cursor, String col) {
        return cursor.getString(cursor.getColumnIndex(col));
    }

    private WatchDogTimer watchDogTimer;

    public void stopListening() {
        if (observer != null) {
            Tools.getApplicationContext().getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
    }

    public void listenForContactsChanges() {
        watchDogTimer = new WatchDogTimer("contact changes", () -> service.addJob(new ExamineJob()), 20, 100, 100);
        Context context = Tools.getApplicationContext();
        observer = new ContentObserver(null) {
            @Override
            public boolean deliverSelfNotifications() {
                Lok.debug("AndroidContactsServerService.deliverSelfNotifications");
                return super.deliverSelfNotifications();
            }

            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                try {
                    if (androidContactSettings.getPersistToPhoneBook()) {
                        if (!selfChange)
                            watchDogTimer.start();
                        else
                            Lok.debug("AndroidServiceMethods.onChange.selfChange");
                    } else {
                        stopListening();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
            }
        };
        context.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer);
    }

    public void onShutDown() {
        if (observer != null)
            Tools.getApplicationContext().getContentResolver().unregisterContentObserver(observer);
    }

}

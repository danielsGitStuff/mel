package de.mein.android.contacts;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import de.mein.android.Tools;
import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.ContactEmail;
import de.mein.contacts.data.db.ContactPhone;
import de.mein.contacts.data.db.ContactStructuredName;
import de.mein.contacts.data.db.ContactsDatabaseManager;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.data.db.dao.ContactsDao;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.sql.SqlQueriesException;


/**
 * Created by xor on 10/4/17.
 */

public class AndroidServiceMethods {

    private final ContactsDatabaseManager databaseManager;

    public AndroidServiceMethods(ContactsDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }


    /**
     * @return flat {@link PhoneBook}
     * @throws SqlQueriesException
     */
    public PhoneBook examineContacts() throws SqlQueriesException {
        ContactsDao contactsDao = databaseManager.getContactsDao();
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        PhoneBook phoneBook;
        PhoneBook master = databaseManager.getFlatMasterPhoneBook();
        if (master == null)
            phoneBook = phoneBookDao.create(0L);
        else
            phoneBook = phoneBookDao.create(master.getVersion().v() + 1);
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


            readName(contact, rawContactId);
            readPhone(contact, rawContactId);
            readEmail(contact, rawContactId);
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

    private void readPhone(Contact contact, String rawContactId) {
        DataTableCursorReader reader = DataTableCursorReader.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{rawContactId}, null);
        while (reader.moveToNext()) {
            ContactPhone phone = new ContactPhone();
            phone.getContactId().v(contact.getId());
            reader.readDataColumns(phone);
            contact.addPhone(phone);
        }
        reader.close();
    }


    private void readName(Contact contact, String rawContactId) {
        String selection = ContactsContract.Data.RAW_CONTACT_ID + " = ? and " + ContactsContract.Data.MIMETYPE + "=?";
        DataTableCursorReader reader = DataTableCursorReader.query(ContactsContract.Data.CONTENT_URI, selection, new String[]{rawContactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
        while (reader.moveToNext()) {
            ContactStructuredName name = new ContactStructuredName();
            name.getContactId().v(contact.getId());
            reader.readDataColumns(name);
            contact.addName(name);
        }
        reader.close();
    }


    private byte[] bitmapToBytes(Bitmap bitmap) {
        int size = bitmap.getRowBytes() * bitmap.getHeight();
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        bitmap.copyPixelsToBuffer(byteBuffer);
        return byteBuffer.array();
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

    private void readEmail(Contact contact, String rawContactId) {
        DataTableCursorReader reader = DataTableCursorReader.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{rawContactId}, null);
        while (reader.moveToNext()) {
            ContactEmail email = new ContactEmail();
            email.getContactId().v(contact.getId());
            reader.readDataColumns(email);
            contact.addEmail(email);
        }
        reader.close();
    }


    public String read(Cursor cursor, String col) {
        return cursor.getString(cursor.getColumnIndex(col));
    }


}

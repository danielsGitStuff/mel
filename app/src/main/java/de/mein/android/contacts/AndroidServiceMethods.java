package de.mein.android.contacts;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract;

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
        PhoneBook phoneBook = phoneBookDao.create();

        String[] projContact = new String[]{
                ContactsContract.Contacts._ID,
//                ContactsContract.Contacts.DISPLAY_NAME,
//                ContactsContract.Contacts.HAS_PHONE_NUMBER,
//                ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE,
//                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
//                ContactsContract.Contacts.DISPLAY_NAME_SOURCE,
                ContactsContract.Contacts.NAME_RAW_CONTACT_ID
        };
        ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
        Cursor contactCursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projContact, null, null, null);
        // Iterate every contact in the phone
        while (contactCursor.moveToNext()) {
            // read rawId first
            Contact contact = new Contact();
            contact.getAndroidId().v(contactCursor.getLong(contactCursor.getColumnIndex(ContactsContract.Contacts.NAME_RAW_CONTACT_ID)));
            String contactId = contact.getAndroidId().v().toString();

            readName(contact, contactId);
            readPhone(contact, contactId);
            readEmail(contact, contactId);
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

    private void readPhone(Contact contact, String contactId) {
        DataTableCursorReader reader = DataTableCursorReader.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{contactId}, null);
        while (reader.moveToNext()) {
            ContactPhone phone = new ContactPhone();
            phone.getContactId().v(contact.getId());
            reader.readDataColumns(phone);
            contact.addPhone(phone);
        }
        reader.close();
    }


    private void readName(Contact contact, String contactId) {
        String selection = ContactsContract.Data.RAW_CONTACT_ID + " = ? and " + ContactsContract.Data.MIMETYPE + "=?";
        DataTableCursorReader reader = DataTableCursorReader.query(ContactsContract.Data.CONTENT_URI, selection, new String[]{contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
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

    private void readPhoto(Contact contact, String contactId) {
        InputStream inputStream = ContactsContract.Contacts.openContactPhotoInputStream(Tools.getApplicationContext().getContentResolver(),
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.valueOf(contactId)));
        try {
            if (inputStream != null) {
                Bitmap photo = BitmapFactory.decodeStream(inputStream);
                contact.getImage().v(bitmapToBytes(photo));
            }
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void readEmail(Contact contact, String contactId) {
        DataTableCursorReader reader = DataTableCursorReader.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{contactId}, null);
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

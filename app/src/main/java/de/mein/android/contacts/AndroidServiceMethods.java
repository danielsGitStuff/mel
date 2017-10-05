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
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.DISPLAY_NAME_SOURCE,
        };
        ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
        Cursor contactCursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projContact, null, null, null);
        // Iterate every contact in the phone
        while (contactCursor.moveToNext()) {
            Contact contact = new Contact();
            String contactId = readContact(contact, contactCursor);

            int hasPhoneNumber = Integer.parseInt(contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)));
            if (hasPhoneNumber > 0) {
                readPhone(contact, contactId);
            }
            readEmail(contact, contactId);
            readPhoto(contact, contactId);
            contact.hash();
            contact.getPhonebookId().v(phoneBook.getId());
            contactsDao.insert(contact);
            phoneBook.hashContact(contact);
            System.out.println("MainActivity.examineContacts: ");
        }
        phoneBook.digest();
        phoneBookDao.updateFlat(phoneBook);
        contactCursor.close();
        return phoneBook;
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
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void readEmail(Contact contact, String contactId) {
        String[] projPhone = new String[]{
                ContactsContract.CommonDataKinds.Email.DATA1,
                ContactsContract.CommonDataKinds.Email.DATA2,
                ContactsContract.CommonDataKinds.Email.DATA3,
                ContactsContract.CommonDataKinds.Email.DATA4,
                ContactsContract.CommonDataKinds.Email.DATA5,
                ContactsContract.CommonDataKinds.Email.DATA6,
                ContactsContract.CommonDataKinds.Email.DATA7,
                ContactsContract.CommonDataKinds.Email.DATA8,
                ContactsContract.CommonDataKinds.Email.DATA9,
                ContactsContract.CommonDataKinds.Email.DATA10,
                ContactsContract.CommonDataKinds.Email.DATA11,
                ContactsContract.CommonDataKinds.Email.DATA12,
                ContactsContract.CommonDataKinds.Email.DATA13,
                ContactsContract.CommonDataKinds.Email.DATA14,
                ContactsContract.CommonDataKinds.Email.DATA15,
                ContactsContract.CommonDataKinds.Email._ID
        };
        ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
        Cursor emailCursor = contentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, projPhone, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{contactId}, null);
        while (emailCursor.moveToNext()) {
            ContactEmail email = new ContactEmail();
            for (int i = 0; i < 15; i++) {
                email.setValue(i, emailCursor.getString(i));
            }
            Long androidId = emailCursor.getLong(15);
            email.setAndroidId(androidId);
            contact.addEmail(email);
        }
        emailCursor.close();
    }


    private String readContact(Contact contact, Cursor cursor) {
        contact.getDisplayName().v(read(cursor, ContactsContract.Contacts.DISPLAY_NAME));
        contact.getDisplayNameAlternative().v(read(cursor, ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE));
        contact.getDisplayNamePrimary().v(read(cursor, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
        contact.getDisplayNameSource().v(read(cursor, ContactsContract.Contacts.DISPLAY_NAME_SOURCE));
        contact.getAndroidId().v(cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID)));
        return read(cursor, ContactsContract.Contacts._ID);
    }

    public String read(Cursor cursor, String col) {
        return cursor.getString(cursor.getColumnIndex(col));
    }

    private void readPhone(Contact contact, String contactId) {
        String[] projPhone = new String[]{
                ContactsContract.CommonDataKinds.Phone.DATA1,
                ContactsContract.CommonDataKinds.Phone.DATA2,
                ContactsContract.CommonDataKinds.Phone.DATA3,
                ContactsContract.CommonDataKinds.Phone.DATA4,
                ContactsContract.CommonDataKinds.Phone.DATA5,
                ContactsContract.CommonDataKinds.Phone.DATA6,
                ContactsContract.CommonDataKinds.Phone.DATA7,
                ContactsContract.CommonDataKinds.Phone.DATA8,
                ContactsContract.CommonDataKinds.Phone.DATA9,
                ContactsContract.CommonDataKinds.Phone.DATA10,
                ContactsContract.CommonDataKinds.Phone.DATA11,
                ContactsContract.CommonDataKinds.Phone.DATA12,
                ContactsContract.CommonDataKinds.Phone.DATA13,
                ContactsContract.CommonDataKinds.Phone.DATA14,
                ContactsContract.CommonDataKinds.Phone.DATA15,
                ContactsContract.CommonDataKinds.Phone._ID
        };
        ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
        Cursor phoneCursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projPhone, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{contactId}, null);
        while (phoneCursor.moveToNext()) {
            ContactPhone phone = new ContactPhone();
            for (int i = 0; i < 15; i++) {
                phone.setValue(i, phoneCursor.getString(i));
            }
            phone.setAndroidId(phoneCursor.getLong(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)));
            contact.addPhone(phone);
        }
        phoneCursor.close();
    }
}

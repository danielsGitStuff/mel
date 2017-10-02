package de.mein.android.contacts.service;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import de.mein.android.Tools;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.ContactsSettings;
import mein.de.contacts.data.db.Contact;
import mein.de.contacts.data.db.ContactEmail;
import mein.de.contacts.data.db.ContactPhone;
import mein.de.contacts.service.ContactsServerService;

/**
 * Created by xor on 10/3/17.
 */

public class AndroidContactsServerService extends ContactsServerService {
    public AndroidContactsServerService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
    }


    @Override
    protected void workWork(Job job) throws Exception {
        System.out.println("AndroidContactsServerService.workWork");
    }

    @Override
    public void onMeinAuthIsUp() {
        super.onMeinAuthIsUp();
        System.out.println("AndroidContactsServerService.onMeinAuthIsUp");
        List<Contact> contacts = getContacts();
        try {
            databaseManager.getContactsDao().deleteAll();
            for (Contact contact : contacts) {
                databaseManager.getContactsDao().insert(contact);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    public List<Contact> getContacts() {
        List<String> contactList = new ArrayList<String>();
        String[] projContact = new String[]{
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.DISPLAY_NAME_SOURCE,
        };
        StringBuffer output;
        List<Contact> contacts = new ArrayList<>();
        ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
        Cursor contactCursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projContact, null, null, null);
        // Iterate every contact in the phone
        while (contactCursor.moveToNext()) {
            Contact contact = new Contact();
            contacts.add(contact);
            String contactId = readContact(contact, contactCursor);

            //String name = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            int hasPhoneNumber = Integer.parseInt(contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)));
            if (hasPhoneNumber > 0) {
                readPhone(contact, contactId);
            }
            readEmail(contact, contactId);
            readPhoto(contact, contactId);
            System.out.println("MainActivity.getContacts: ");
        }
        contactCursor.close();
        return contacts;
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
        };
        ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
        Cursor emailCursor = contentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, projPhone, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{contactId}, null);
        while (emailCursor.moveToNext()) {
            ContactEmail contactEmail = new ContactEmail();
            for (int i = 0; i < projPhone.length; i++) {
                contactEmail.setValue(i, emailCursor.getString(i));
            }
            contact.addEmail(contactEmail);
        }
        emailCursor.close();
    }


    private String readContact(Contact contact, Cursor cursor) {
        contact.getDisplayName().v(read(cursor, ContactsContract.Contacts.DISPLAY_NAME));
        contact.getDisplayNameAlternative().v(read(cursor, ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE));
        contact.getDisplayNamePrimary().v(read(cursor, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
        contact.getDisplayNameSource().v(read(cursor, ContactsContract.Contacts.DISPLAY_NAME_SOURCE));
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
        };
        ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
        Cursor phoneCursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projPhone, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{contactId}, null);
        while (phoneCursor.moveToNext()) {
            ContactPhone contactPhone = new ContactPhone();
            for (int i = 0; i < projPhone.length; i++) {
                contactPhone.setValue(i, phoneCursor.getString(i));
            }
            contact.addPhone(contactPhone);
        }
        phoneCursor.close();
    }
}

package de.mein.android.contacts;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;

import de.mein.android.Tools;
import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.ContactAppendix;
import de.mein.contacts.data.db.ContactEmail;
import de.mein.contacts.data.db.ContactPhone;
import de.mein.contacts.data.db.ContactStructuredName;
import de.mein.contacts.data.db.ContactsDatabaseManager;
import de.mein.contacts.data.db.dao.ContactsDao;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 10/6/17.
 */

public class ContactsToAndroidExporter{
    private final ContactsDatabaseManager databaseManager;

    public ContactsToAndroidExporter(ContactsDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void export(final Long phoneBookId) {
        // delete everything first
        try {
            ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
            Cursor cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
            while (cursor.moveToNext()) {
                String lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                contentResolver.delete(uri, null, null);
            }
            ContactsDao contactsDao = databaseManager.getContactsDao();
            List<Contact> contacts = contactsDao.getContacts(phoneBookId);
            for (Contact contact : contacts) {
                // first create a raw contact
                ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
                operationList.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build());

                // then appendices in the according tables
                for (ContactStructuredName name : contact.getNames()) {
                    operationList.add(insertAppendix(name,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE));
                }
                for (ContactPhone phone : contact.getPhones()) {
                    operationList.add(insertAppendix(phone,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE));
                }
                for (ContactEmail email : contact.getEmails()) {
                    operationList.add(insertAppendix(email,ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE));
                }
//                operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
//                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
//                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
//                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "09876543210")
//                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
//                        .build());
//                operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
//                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
//
//                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
//                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, "abc@xyz.com")
//                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
//                        .build());
            }
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
    }

    private ContentProviderOperation insertAppendix(ContactAppendix appendix, String contentItemType) {
        // reverse the reading process
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, contentItemType);
        String[] columns = DataTableCursorReader.createWriteDataColumnNames();
        for (int i = 0; i < columns.length; i++) {
            builder.withValue(columns[i], appendix.getValue(i));
        }
        return builder.build();
    }
}

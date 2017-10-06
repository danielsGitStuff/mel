package de.mein.android.contacts;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
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
import de.mein.sql.Pair;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 10/6/17.
 */

public class ContactsToAndroidExporter {
    private final ContactsDatabaseManager databaseManager;

    public ContactsToAndroidExporter(ContactsDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    private List<Pair<String>> readCols(Cursor cursor) {
        List<Pair<String>> names = new ArrayList<>();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            names.add(new Pair<>(String.class, cursor.getColumnName(i), cursor.getString(i)));
        }
        return names;
    }

    public void export(final Long phoneBookId) {
        try {
            ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
            // delete everything first

            Cursor cursor = contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, new String[]{ContactsContract.RawContacts._ID}, null, null, null);
            while (cursor.moveToNext()) {
                String _id = cursor.getString(cursor.getColumnIndex(ContactsContract.RawContacts._ID));
                List<Pair<String>> colNames = readCols(cursor);
//                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, _id);
                uri = ContactsContract.RawContacts.CONTENT_URI;

                int delrows = contentResolver.delete(uri, ContactsContract.RawContacts._ID + "=?", new String[]{_id});
                System.out.println("ContactsToAndroidExporter.export");
            }


//            Cursor cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
//            while (cursor.moveToNext()) {
//                String _id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
//                List<Pair<String>> colNames = readCols(cursor);
////                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
//                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, _id);
//                uri = ContactsContract.Contacts.CONTENT_URI;
//
//                int delrows = contentResolver.delete(uri, ContactsContract.Contacts._ID+"=?", new String[]{_id});
//                System.out.println("ContactsToAndroidExporter.export");
//            }
            Uri deleteRawUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
            contentResolver.delete(deleteRawUri, null, null);
            ContactsDao contactsDao = databaseManager.getContactsDao();
            List<Contact> contacts = contactsDao.getContacts(phoneBookId);
            for (Contact contact : contacts) {
                try {

                    // first create a raw contact
                    ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
                    operationList.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                            .build());

                    // then appendices in the according tables
                    insertAppendices(operationList, contact.getId().v(), ContactStructuredName.class, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                    insertAppendices(operationList, contact.getId().v(), ContactPhone.class, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                    insertAppendices(operationList, contact.getId().v(), ContactEmail.class, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);

//                    for (ContactStructuredName name : contactsDao.getAppendix(contact.getId().v(), ContactStructuredName.class)) {
//                        operationList.add(insertAppendix(name, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE));
//                    }
//                    for (ContactPhone phone : contact.getPhones()) {
//                        operationList.add(insertAppendix(phone, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE));
//                    }
//                    for (ContactEmail email : contact.getEmails()) {
//                        operationList.add(insertAppendix(email, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE));
//                    }
                    // save photo
                    if (contact.getImage().notNull()) {
                        operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, contact.getImage().v())
                                .build());
                    }
                    contentResolver.applyBatch(ContactsContract.AUTHORITY, operationList);
                } catch (RemoteException | OperationApplicationException | IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            }
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
    }

    private <T extends ContactAppendix> void insertAppendices(ArrayList<ContentProviderOperation> operationList, Long contactId, Class<T> appendixClass, String contentItemType) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        List<T> appendices = databaseManager.getContactsDao().getAppendix(contactId, appendixClass);
        for (ContactAppendix appendix : appendices) {
            operationList.add(insertAppendix(appendix, contentItemType));
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

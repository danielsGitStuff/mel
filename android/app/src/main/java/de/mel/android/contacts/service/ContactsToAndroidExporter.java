package de.mel.android.contacts.service;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;

import de.mel.Lok;
import de.mel.android.Tools;
import de.mel.android.contacts.service.AndroidServiceMethods;
import de.mel.android.contacts.service.DataTableCursorReader;
import de.mel.contacts.data.db.Contact;
import de.mel.contacts.data.db.ContactAppendix;
import de.mel.contacts.data.db.ContactsDatabaseManager;
import de.mel.contacts.data.db.PhoneBook;
import de.mel.contacts.data.db.dao.ContactsDao;
import de.mel.sql.MD5er;
import de.mel.sql.Pair;
import de.mel.sql.SqlQueriesException;

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
            Lok.debug("id." + phoneBookId);
            //check if phonebooks are different first

            ContentResolver contentResolver = Tools.getApplicationContext().getContentResolver();
            // delete everything first
            Cursor cursor = contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, new String[]{ContactsContract.RawContacts._ID}, null, null, null);
            while (cursor.moveToNext()) {
                String _id = cursor.getString(cursor.getColumnIndex(ContactsContract.RawContacts._ID));
                List<Pair<String>> colNames = readCols(cursor);
                Uri uri = ContactsContract.RawContacts.CONTENT_URI;

                int delrows = contentResolver.delete(uri, ContactsContract.RawContacts._ID + "=?", new String[]{_id});
            }


            // we have to rehash everything we insert because inserting a contact image will slightly alter it.
            PhoneBook phoneBook = databaseManager.getPhoneBookDao().loadFlatPhoneBook(phoneBookId);
            phoneBook.resetHash();
            ContactsDao contactsDao = databaseManager.getContactsDao();
            List<Contact> contacts = contactsDao.getContacts(phoneBookId);
            for (Contact contact : contacts) {
                try {
                    MD5er contactMD5er = new MD5er();
                    // first create a raw contact
                    ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
                    operationList.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                            .build());

                    // then appendices in the according tables. note: order is the same as in Contact.hash()
                    insertAppendices(operationList, contact.getId().v(), ContactsContract.Data.CONTENT_TYPE, contactMD5er);
//                    insertAppendices(operationList, contact.getId().v(), StructuredName.class, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, contactMD5er);
//                    insertAppendices(operationList, contact.getId().v(), ContactPhone.class, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, contactMD5er);

                    // save photo
                    if (contact.getImage().notNull()) {
                        operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, contact.getImage().v())
                                .build());
                    }
                    // read the inserted image. it probably has changed
                    Contact dummy = new Contact();
                    ContentProviderResult[] result = contentResolver.applyBatch(ContactsContract.AUTHORITY, operationList);
                    AndroidServiceMethods.readPhoto(dummy, result[0].uri.getLastPathSegment());
                    contactMD5er.hash(dummy.getImage().v());
                    //contactMD5er.hash(contact.getImage().v());
                    contact.getHash().v(contactMD5er.digest());
                    contactsDao.updateHash(contact);
                    phoneBook.hashContact(contact);
                } catch (RemoteException | OperationApplicationException | IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            }
            phoneBook.digest();
            databaseManager.getPhoneBookDao().updateFlat(phoneBook);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
    }

    private void insertAppendices(ArrayList<ContentProviderOperation> operationList, Long contactId, String contentItemType, MD5er md5er) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        List<ContactAppendix> appendices = databaseManager.getContactsDao().getAppendices(contactId);
        for (ContactAppendix appendix : appendices) {
            // images are inserted differently
            if (appendix.getMimeType().notEqualsValue(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE))
                operationList.add(insertAppendix(appendix, contentItemType, md5er));
        }
    }

    private ContentProviderOperation insertAppendix(ContactAppendix appendix, String contentItemType, MD5er md5er) {
        // reverse the reading process
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, contentItemType);
        String[] columns = DataTableCursorReader.createWriteDataColumnNames();
        for (int i = 0; i < appendix.getNoOfColumns(); i++) {
            builder.withValue(columns[i], appendix.getValue(i));
            md5er.hash(appendix.getValue(i));
        }
        md5er.hash(appendix.getBlob().v());
        md5er.hash(appendix.getMimeType().v());
        builder.withValue(ContactsContract.Data.MIMETYPE, appendix.getMimeType().v());
        return builder.build();
    }
}

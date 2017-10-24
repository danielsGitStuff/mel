package de.mein.android.contacts.service;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;

import de.mein.android.Tools;
import de.mein.contacts.data.db.ContactAppendix;

/**
 * Created by xor on 10/6/17.
 */
public class DataTableCursorReader {
    private Cursor cursor;

    public DataTableCursorReader(Cursor cursor) {
        this.cursor = cursor;
    }

    public static DataTableCursorReader query(Uri uri, String selection, String[] selectionArgs, String sortOrder) {
        return new DataTableCursorReader(Tools.getApplicationContext().getContentResolver().query(uri, createReadDataColumnNames(), selection, selectionArgs, sortOrder));
    }

    public static String[] createReadDataColumnNames() {
        String[] columns = new String[17];
        for (int i = 0; i < 15; i++) {
            columns[i] = "data" + (i + 1);
        }
        columns[15] = BaseColumns._ID;
        columns[16] = ContactsContract.Data.MIMETYPE;
        return columns;
    }

    public static String[] createWriteDataColumnNames() {
        String[] columns = new String[15];
        for (int i = 0; i < 15; i++) {
            columns[i] = "data" + (i + 1);
        }
        return columns;
    }

    public DataTableCursorReader readDataColumns(ContactAppendix appendix) {
        for (int i = 0; i < 14; i++) {
            try {
                appendix.setValue(i, cursor.getString(i));
            } catch (Exception e) {
                System.err.println("i " + i + " mime " + appendix.getMimeType().v());
            }
        }
        appendix.setBlob(cursor.getBlob(14));
        appendix.setAndroidId(cursor.getLong(15));
        appendix.setMimeType(cursor.getString(16));
        return this;
    }

    public void close() {
        cursor.close();
    }

    public boolean moveToNext() {
        return cursor.moveToNext();
    }
}

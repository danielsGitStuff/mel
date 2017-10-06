package de.mein.android.contacts;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

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
        return new DataTableCursorReader(Tools.getApplicationContext().getContentResolver().query(uri, createDataColumnNames(), selection, selectionArgs, sortOrder));
    }

    public static String[] createDataColumnNames() {
        String[] columns = new String[16];
        for (int i = 0; i < 15; i++) {
            columns[i] = "data" + (i + 1);
        }
        columns[15] = BaseColumns._ID;
        return columns;
    }

    public DataTableCursorReader readDataColumns(ContactAppendix appendix) {
        for (int i = 0; i < 15; i++) {
            appendix.setValue(i, cursor.getString(i));
        }
        appendix.setAndroidId(cursor.getLong(15));
        return this;
    }

    public void close() {
        cursor.close();
    }

    public boolean moveToNext() {
        return cursor.moveToNext();
    }
}

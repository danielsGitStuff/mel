package de.mein.android.contacts.data.db;

import android.provider.ContactsContract;

import de.mein.contacts.data.db.AppendixWrapper;

/**
 * Created by xor on 10/17/17.
 */

public class ContactName extends AppendixWrapper {
    @Override
    public String getMimeType() {
        return ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE;
    }

    public String getName() {
        return appendix.getColumnValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
    }

    @Override
    public String toString() {
        return getName();
    }
}

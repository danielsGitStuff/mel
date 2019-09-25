package de.mel.android.contacts.data.db;

import android.provider.ContactsContract;

import de.mel.contacts.data.db.AppendixWrapper;

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

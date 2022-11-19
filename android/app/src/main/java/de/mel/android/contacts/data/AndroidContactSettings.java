package de.mel.android.contacts.data;

import de.mel.contacts.data.PlatformContactSettings;

/**
 * Created by xor on 10/6/17.
 */

public class AndroidContactSettings implements PlatformContactSettings {
    private boolean persistToPhoneBook = true;

    public AndroidContactSettings setPersistToPhoneBook(boolean persistToPhoneBook) {
        this.persistToPhoneBook = persistToPhoneBook;
        return this;
    }

    public boolean getPersistToPhoneBook() {
        return persistToPhoneBook;
    }
}

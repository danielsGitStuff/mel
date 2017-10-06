package de.mein.android.contacts.data;

import de.mein.contacts.data.PlatformContactSettings;

/**
 * Created by xor on 10/6/17.
 */

public class AndroidContactSettings implements PlatformContactSettings {
    private boolean saveToPhoneBook = true;

    public void setSaveToPhoneBook(boolean saveToPhoneBook) {
        this.saveToPhoneBook = saveToPhoneBook;
    }

    public boolean getSaveToPhoneBook() {
        return saveToPhoneBook;
    }
}

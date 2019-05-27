package de.mein.contacts.data.db;

import de.mein.auth.data.ServicePayload;

public class PhoneBookWrapper extends ServicePayload {
    private PhoneBook phoneBook;

    public PhoneBookWrapper() {

    }

    public PhoneBookWrapper(PhoneBook phoneBook) {
        this.phoneBook = phoneBook;
    }

    public PhoneBook getPhoneBook() {
        return phoneBook;
    }
}

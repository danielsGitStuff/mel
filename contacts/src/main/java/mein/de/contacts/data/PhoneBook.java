package mein.de.contacts.data;

import java.util.ArrayList;
import java.util.List;

import de.mein.core.serialize.SerializableEntity;
import mein.de.contacts.data.db.Contact;

/**
 * Created by xor on 10/4/17.
 */

public class PhoneBook implements SerializableEntity {
    private List<Contact> contacts = new ArrayList<>();
    private Long version;

    public PhoneBook() {

    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
    }
}

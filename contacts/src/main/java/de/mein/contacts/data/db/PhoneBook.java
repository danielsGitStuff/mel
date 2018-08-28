package de.mein.contacts.data.db;

import java.util.ArrayList;
import java.util.List;

import de.mein.auth.data.IPayload;
import de.mein.core.serialize.JsonIgnore;
import de.mein.sql.MD5er;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * A list of all contacts a version number and the ability to hash its content.
 * A flat {@link PhoneBook} does not contain any {@link Contact}s but a hash value and version.
 * Created by xor on 10/4/17.
 */
public class PhoneBook extends SQLTableObject implements IPayload {
    private List<Contact> contacts = new ArrayList<>();
    private Pair<Long> version = new Pair<>(Long.class, "version");
    private Pair<Long> created = new Pair<>(Long.class, "created");
    @JsonIgnore
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> hash = new Pair<>(String.class, "deephash");
    /**
     * true if PhoneBook is read from the local computer
     */
    @JsonIgnore
    private Pair<Boolean> original = new Pair<Boolean>(Boolean.class, "org");
    private MD5er md5er = new MD5er();

    public PhoneBook() {
        init();
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public Pair<Long> getVersion() {
        return version;
    }

    public Pair<Boolean> getOriginal() {
        return original;
    }

    public Pair<Long> getCreated() {
        return created;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<String> getHash() {
        return hash;
    }

    public void addContact(Contact contact) {
        contacts.add(contact);
    }

    @Override
    public String getTableName() {
        return "phonebook";
    }

    @Override
    protected void init() {
        populateInsert(version, hash, created, original);
        populateAll(id);
    }

    public void resetHash() {
        md5er = new MD5er();
    }

    /**
     * calculates a hash based on all {@link Contact} hashes this {@link PhoneBook} contains.
     */
    public void hash() {
        for (Contact contact : contacts) {
            md5er.hash(contact.getHash().v());
        }
        hash.v(md5er.digest());
    }


    /**
     * updates the current hash with
     *
     * @param contact
     */
    public void hashContact(Contact contact) {
        md5er.hash(contact.getHash().v());
    }

    /**
     * @return hash based on all {@link Contact}s thrown into hashContact(). <br>
     * Note: the hashing object will be reset afterwards.<br>
     * Call resetHash() and hashContact() if you want a new valid hash.
     */
    public String digest() {
        hash.v(md5er.digest());
        return hash.v();
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
    }
}

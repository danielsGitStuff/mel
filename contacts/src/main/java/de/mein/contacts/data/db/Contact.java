package de.mein.contacts.data.db;

import java.util.ArrayList;
import java.util.List;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.MD5er;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/22/17.
 */

public class Contact extends SQLTableObject implements SerializableEntity {

    public static final String ID = "id";
    public static final String PID = "pid";
    public static final String IMAGE = "image";
    public static final String AID = "aid";
    public static final String HASH = "deephash";
    @JsonIgnore
    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<Long> phonebookId = new Pair<>(Long.class, PID);

    private Pair<byte[]> image = new Pair<>(byte[].class, IMAGE);

    private List<ContactPhone> phones = new ArrayList<>();
    private List<ContactEmail> emails = new ArrayList<>();
    private List<ContactStructuredName> names = new ArrayList<>();

    @JsonIgnore
    private Pair<Long> androidId = new Pair<>(Long.class, AID);
    private Pair<String> hash = new Pair<>(String.class, HASH);

    public Contact() {
        init();
    }


    public Pair<Long> getId() {
        return id;
    }


    public Pair<Long> getPhonebookId() {
        return phonebookId;
    }

    public Pair<byte[]> getImage() {
        return image;
    }

    @Override
    public String getTableName() {
        return "contacts";
    }

    @Override
    protected void init() {
        populateInsert(phonebookId, image);
        insertAttributes.add(hash);
        populateAll(id);
    }

    public void addPhone(ContactPhone contactPhone) {
        phones.add(contactPhone);
    }

    public void addEmail(ContactEmail contactEmail) {
        emails.add(contactEmail);
    }

    public List<ContactPhone> getPhones() {
        return phones;
    }

    public List<ContactEmail> getEmails() {
        return emails;
    }

    public Pair<Long> getAndroidId() {
        return androidId;
    }

    public Pair<String> getHash() {
        return hash;
    }

    /**
     * hashes but does not digest md5er
     *
     * @param md5er
     */
    public void hash(MD5er md5er) {
        hash();
        Pair.hash(md5er, hash);
    }

    public String hash() {
        MD5er md5er = Pair.hash(new MD5er(), insertAttributes);
        for (ContactPhone phone : phones)
            phone.hash(md5er);
        for (ContactEmail email : emails)
            email.hash(md5er);
        for (ContactStructuredName name: names)
            name.hash(md5er);
        md5er.hash(image.v());
        hash.v(md5er.digest());
        return hash.v();
    }

    public void addName(ContactStructuredName name) {
        names.add(name);
    }

    public List<ContactStructuredName> getNames() {
        return names;
    }
}

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
    public static final String ACCOUNT_TYPE = "accounttype";
    public static final String ACCOUNT_NAME = "accountname";
    public static final String DISPLAYNAMEALTERNATIVE = "displaynamealternative";
    public static final String DISPLAYNAMEPRIMITIVE = "displaynameprimary";
    public static final String DISPLAYNAMESOURCE = "displaynamesource";
    public static final String IMAGE = "image";
    public static final String AID = "aid";
    public static final String HASH = "deephash";
    @JsonIgnore
    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<Long> phonebookId = new Pair<>(Long.class, PID);

    private Pair<String> accountType = new Pair<>(String.class, ACCOUNT_TYPE);
    private Pair<String> accountName = new Pair<>(String.class, ACCOUNT_NAME);
    private Pair<String> displayNameAlternative = new Pair<>(String.class, DISPLAYNAMEALTERNATIVE);

    private Pair<String> displayNamePrimary = new Pair<>(String.class, DISPLAYNAMEPRIMITIVE);

    private Pair<String> displayNameSource = new Pair<>(String.class, DISPLAYNAMESOURCE);
    private Pair<byte[]> image = new Pair<>(byte[].class, IMAGE);

    private List<ContactPhone> phones = new ArrayList<>();
    private List<ContactEmail> emails = new ArrayList<>();
    @JsonIgnore
    private Pair<Long> androidId = new Pair<>(Long.class, AID);
    private Pair<String> hash = new Pair<>(String.class, HASH);
    private List<Pair<?>> hashPairs;

    public Contact() {
        init();
    }

    public Pair<String> getAccountType() {
        return accountType;
    }

    public Pair<String> getAccountName() {
        return accountName;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<String> getDisplayNameAlternative() {
        return displayNameAlternative;
    }

    public Pair<String> getDisplayNamePrimary() {
        return displayNamePrimary;
    }

    public Pair<String> getDisplayNameSource() {
        return displayNameSource;
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
        populateInsert(phonebookId, accountType, accountName, displayNameAlternative, displayNamePrimary, displayNameSource, image);
        hashPairs = new ArrayList<>(insertAttributes);
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
        hash.v(md5er.digest());
        return hash.v();
    }
}

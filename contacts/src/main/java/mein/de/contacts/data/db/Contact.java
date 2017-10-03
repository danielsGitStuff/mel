package mein.de.contacts.data.db;

import java.util.ArrayList;
import java.util.List;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/22/17.
 */

public class Contact extends SQLTableObject implements SerializableEntity {

    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> displayName = new Pair<>(String.class, "displayname");
    private Pair<String> displayNameAlternative = new Pair<>(String.class, "displaynamealternative");

    private Pair<String> displayNamePrimary = new Pair<>(String.class, "displaynameprimitive");

    private Pair<String> displayNameSource = new Pair<>(String.class, "displaynamesource");
    private Pair<byte[]> image = new Pair<>(byte[].class, "image");

    private List<ContactPhone> phones = new ArrayList<>();
    private List<ContactEmail> emails = new ArrayList<>();
    private Pair<Long> androidId = new Pair<>(Long.class,"aid");

    public Contact() {
        init();
    }

    public Pair<String> getDisplayName() {
        return displayName;
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


    public Pair<byte[]> getImage() {
        return image;
    }

    @Override
    public String getTableName() {
        return "contacts";
    }

    @Override
    protected void init() {
        populateInsert(displayName, displayNameAlternative, displayNamePrimary, displayNameSource,image);
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
}

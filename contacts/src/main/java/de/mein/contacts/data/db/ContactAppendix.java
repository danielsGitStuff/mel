package de.mein.contacts.data.db;

import java.util.ArrayList;
import java.util.List;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.MD5er;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/23/17.
 */

public abstract class ContactAppendix extends SQLTableObject implements SerializableEntity {

    @JsonIgnore
    public static final String ID = "id";
    public static final String CONTACTID = "contactid";
    public static final String AID = "aid";
    @JsonIgnore
    private List<Pair<?>> hashPairs;

    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<Long> contactId = new Pair<>(Long.class, CONTACTID);
    @JsonIgnore
    private Pair<Long> androidId = new Pair<>(Long.class, AID);

    public Pair<Long> getId() {
        return id;
    }

    public Pair<Long> getContactId() {
        return contactId;
    }

    @Override
    protected void init() {
        populateInsert();
        for (int i = 1; i < 16; i++) {
            String col = "data" + i;
            insertAttributes.add(new Pair<>(String.class, col));
        }
        hashPairs = new ArrayList<>(insertAttributes);
        insertAttributes.add(contactId);
        insertAttributes.add(androidId);
        populateAll(id);
    }

    public void setValue(int index, String value) {
        insertAttributes.get(index).setValueUnsecure(value);
    }

    public Pair<Long> getAndroidId() {
        return androidId;
    }

    public void setAndroidId(Long androidId) {
        this.androidId.v(androidId);
    }

    public MD5er hash(MD5er md5er) {
        return Pair.hash(md5er,hashPairs);
    }
}

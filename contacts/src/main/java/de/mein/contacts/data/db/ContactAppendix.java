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

public class ContactAppendix extends SQLTableObject implements SerializableEntity {


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

    private List<Pair<String>> dataCols = new ArrayList<>(15);

    @Override
    public String getTableName() {
        return "appendix";
    }

    @Override
    protected void init() {
        populateInsert();
        for (int i = 1; i < 16; i++) {
            String col = "data" + i;
            Pair<String> pair = new Pair<>(String.class, col);
            insertAttributes.add(pair);
            dataCols.add(pair);
        }
        hashPairs = new ArrayList<>(insertAttributes);
        insertAttributes.add(contactId);
        insertAttributes.add(androidId);
        populateAll(id);
    }

    public void setValue(int index, String value) {
        dataCols.get(index).setValueUnsecure(value);
    }

    public Pair<Long> getAndroidId() {
        return androidId;
    }

    public void setAndroidId(Long androidId) {
        this.androidId.v(androidId);
    }

    public MD5er hash(MD5er md5er) {
        return Pair.hash(md5er, hashPairs);
    }

    public Object getValue(int index) {
        return dataCols.get(index).v();
    }

    public List<Pair<String>> getDataCols() {
        return dataCols;
    }
}

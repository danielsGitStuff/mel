package de.mein.contacts.data.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.MD5er;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/23/17.
 */

public class ContactAppendix extends SQLTableObject implements SerializableEntity {

    protected Contact contact;

    public ContactAppendix() {
        init();
    }

    public ContactAppendix(Contact contact) {
        this.contact = contact;
        init();
    }


    public static final String ID = "id";
    public static final String CONTACTID = "contactid";
    public static final String AID = "aid";
    public static final String MIMETYPE = "mime";
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
    @JsonIgnore
    private Map<String, Pair<String>> dataColMap = new HashMap<>();
    private Pair<String> mimeType = new Pair<String>(String.class, MIMETYPE);
    private Pair<byte[]> blob = new Pair<>(byte[].class, "data15");

    @Override
    public String getTableName() {
        return "appendix";
    }

    @Override
    protected void init() {
        populateInsert();
        for (int i = 1; i < 15; i++) {
            String col = "data" + i;
            Pair<String> pair = new Pair<>(String.class, col);
            insertAttributes.add(pair);
            dataCols.add(pair);
            dataColMap.put(col, pair);
        }
        insertAttributes.add(blob);
        insertAttributes.add(mimeType);
        hashPairs = new ArrayList<>(insertAttributes);
        insertAttributes.add(contactId);
        insertAttributes.add(androidId);
        populateAll(id);
    }

    public void setValue(int index, String value) {
        dataCols.get(index).setValueUnsecure(value);
    }

    public Pair<String> getMimeType() {
        return mimeType;
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

    public void setMimeType(String mimeType) {
        this.mimeType.v(mimeType);
    }

    /**
     * returns values from 'data' columns
     *
     * @param columnName
     * @return
     */
    public String getColumnValue(String columnName) {
        Pair<String> pair = dataColMap.get(columnName);
        return pair == null ? null : pair.v();
    }

    public void setBlob(byte[] blob) {
        this.blob.v(blob);
    }

    public Pair<byte[]> getBlob() {
        return blob;
    }
}

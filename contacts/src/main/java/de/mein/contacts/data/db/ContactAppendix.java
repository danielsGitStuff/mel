package de.mein.contacts.data.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.sql.MD5er;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;
import de.mein.sql.deserialize.PairCollectionDeserializerFactory;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairCollectionSerializer;
import de.mein.sql.serialize.PairCollectionSerializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;

/**
 * Created by xor on 9/23/17.
 */

public class ContactAppendix extends SQLTableObject implements SerializableEntity {

    protected Contact contact;
    @JsonIgnore
    private Integer noOfColumns = 15;

    public ContactAppendix() {
        init();
    }

    public ContactAppendix(String mimeType) {
        this.noOfColumns = ContactAppendix.getNoOfColumnsByMime(mimeType);
        this.mimeType.v(mimeType);
        init();
    }

    public ContactAppendix(Contact contact) {
        this.contact = contact;
        init();
    }

    public Integer getNoOfColumns() {
        return noOfColumns;
    }

    protected int noOfDataColumns() {
        return noOfColumns;
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

    private List<Pair<String>> dataCols;
    @JsonIgnore
    private Map<String, Pair<String>> dataColMap;
    private Pair<String> mimeType = new Pair<String>(String.class, MIMETYPE);
    private Pair<byte[]> blob = new Pair<>(byte[].class, "data15");

    @Override
    public String getTableName() {
        return "appendix";
    }

    public static void main(String[] args) throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance());

        ContactAppendix c1 = new ContactAppendix();
        ContactAppendix c2 = new ContactAppendix("vnd.android.cursor.item/im");
        ContactAppendix c3 = new ContactAppendix();
        c3.getMimeType().v("vnd.android.cursor.item/organization");
        c3.setValue(3,"keks");
        c3.setValue(4,"bla");
        String json1 = SerializableEntitySerializer.serialize(c1);
        String json2 = SerializableEntitySerializer.serialize(c2);
        String json3 = SerializableEntitySerializer.serialize(c3);
        System.out.println(json1);
        System.out.println(json2);
        System.out.println(json3);
        ContactAppendix cc1 = (ContactAppendix) SerializableEntityDeserializer.deserialize(json1);
        ContactAppendix cc2 = (ContactAppendix) SerializableEntityDeserializer.deserialize(json2);
        ContactAppendix cc3 = (ContactAppendix) SerializableEntityDeserializer.deserialize(json3);
        System.out.println("ContactAppendix.main");

    }

    @Override
    protected void init() {
        mimeType.setSetListener(mime -> {
            if (mime!=null) {
                noOfColumns = ContactAppendix.getNoOfColumnsByMime(mime);
                init();
            }
            return mime;
        });
        if (insertAttributes != null) {
            allAttributes = new ArrayList<>();
            hashPairs = new ArrayList<>();
            dataColMap = new HashMap<>();
            dataCols = new ArrayList<>();
            List<Pair<?>> newInserts = new ArrayList<>();
            for (int i = 0; i < noOfColumns; i++) {
                Pair pair = insertAttributes.get(i);
                newInserts.add(pair);
                insertAttributes.add(pair);
                hashPairs.add(pair);
                dataCols.add(pair);
                dataColMap.put("data"+i, pair);
            }
            insertAttributes = newInserts;
        }else {
            insertAttributes = new ArrayList<>();
            allAttributes = new ArrayList<>();
            hashPairs = new ArrayList<>();
            dataColMap = new HashMap<>();
            dataCols = new ArrayList<>();
            for (int i = 1; i <= getNoOfColumns(); i++) {
                String col = "data" + i;
                Pair<String> pair = new Pair<>(String.class, col);
                insertAttributes.add(pair);
                hashPairs.add(pair);
                dataCols.add(pair);
                dataColMap.put(col, pair);
            }
        }
        insertAttributes.add(blob);
        insertAttributes.add(mimeType);
        insertAttributes.add(contactId);
        insertAttributes.add(androidId);
        allAttributes = new ArrayList<>();
        populateAll(id);
    }

    public ContactAppendix setValue(int index, String value) {
        dataCols.get(index).setValueUnsecure(value);
        return this;
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

    /**
     * resets data columns
     *
     * @param mimeType
     */
    public void adjustToMimeType(String mimeType) {
        this.mimeType.v(mimeType);
        noOfColumns = ContactAppendix.getNoOfColumnsByMime(mimeType);
        init();
        hashPairs = new ArrayList<>();
        for (int i = 1; i <= noOfColumns; i++) {

        }

    }

    public static Integer getNoOfColumnsByMime(String mimeType) {
        int noOfColumns = 0;
        if (mimeType.equals("vnd.android.cursor.item/email_v2")
                || mimeType.equals("vnd.android.cursor.item/contact_event")
                || mimeType.equals("vnd.android.cursor.item/nickname")
                || mimeType.equals("vnd.android.cursor.item/phone_v2")
                || mimeType.equals("vnd.android.cursor.item/relation")
                || mimeType.equals("vnd.android.cursor.item/sip_address")
                || mimeType.equals("vnd.android.cursor.item/website"))
            noOfColumns = 3;
        else if (mimeType.equals("vnd.android.cursor.item/note"))
            noOfColumns = 1;
        else if (mimeType.equals("vnd.android.cursor.item/im"))
            noOfColumns = 6;
        else if (mimeType.equals("vnd.android.cursor.item/organization")
                || mimeType.equals("vnd.android.cursor.item/postal-address_v2"))
            noOfColumns = 10;
        else if (mimeType.equals("vnd.android.cursor.item/name"))
            noOfColumns = 9;
        else
            System.err.println(ContactAppendix.class.getSimpleName() + ".MIME.UNKNOWN: " + mimeType);
        return noOfColumns;
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

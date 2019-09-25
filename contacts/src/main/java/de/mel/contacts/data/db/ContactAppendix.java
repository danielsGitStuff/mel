package de.mel.contacts.data.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mel.Lok;
import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.MD5er;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;
import de.mel.sql.deserialize.PairCollectionDeserializerFactory;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairCollectionSerializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;

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

    public ContactAppendix(int noOfColumns) {
        this.noOfColumns = noOfColumns;
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
        ContactAppendix c2 = new ContactAppendix(ContactAppendix.getNoOfColumnsByMime("vnd.android.cursor.item/im"));
        ContactAppendix c3 = new ContactAppendix();
        c3.getMimeType().v("vnd.android.cursor.item/organization");
        c3.setValue(3, "keks");
        c3.setValue(4, "bla");
        String json1 = SerializableEntitySerializer.serialize(c1);
        String json2 = SerializableEntitySerializer.serialize(c2);
        String json3 = SerializableEntitySerializer.serialize(c3);
        Lok.debug(json1);
        Lok.debug(json2);
        Lok.debug(json3);
        ContactAppendix cc1 = (ContactAppendix) SerializableEntityDeserializer.deserialize(json1);
        ContactAppendix cc2 = (ContactAppendix) SerializableEntityDeserializer.deserialize(json2);
        ContactAppendix cc3 = (ContactAppendix) SerializableEntityDeserializer.deserialize(json3);


        Contact contact = new Contact();
        ContactAppendix name = new ContactAppendix(getNoOfColumnsByMime("vnd.android.cursor.item/name"))
                .setValue(0, "Andreas Deibel").setValue(1, "Andreas").setValue(2, "Deibel");
        name.getMimeType().v("vnd.android.cursor.item/name");
        ContactAppendix phone1 = new ContactAppendix(getNoOfColumnsByMime("vnd.android.cursor.item/phone_v2"))
                .setValue(0, "015-208-775497").setValue(1, "2");
        phone1.getMimeType().v("vnd.android.cursor.item/phone_v2");
        ContactAppendix phone2 = new ContactAppendix(getNoOfColumnsByMime("vnd.android.cursor.item/phone_v2")).setValue(0, "015-208-775497").setValue(1, "2");
        phone2.getMimeType().v("vnd.android.cursor.item/phone_v2");
//        contact.hash();

//        ContactAppendix w1 = new ContactAppendix();
//        w1.getMimeType().v("vnd.android.cursor.item/vnd.com.whatsapp.profile");
//        w1.setValue(0, "4915208775497@s.whatsapp.net").setValue(1, "WhatsApp").setValue(2, "Message +49 1520 8775497");
//        ContactAppendix w2 = new ContactAppendix();
//        w2.getMimeType().ignoreSetListener().v("vnd.android.cursor.item/vnd.com.whatsapp.voip.call");
//        w2.setValue(0, "4915208775497@s.whatsapp.net").setValue(1, "WhatsApp").setValue(2, "Voice call +49 1520 8775497");
//        ContactAppendix w3 = new ContactAppendix();
//        w3.getMimeType().ignoreSetListener().v("vnd.android.cursor.item/vnd.com.whatsapp.video.call");
//        w3.setValue(0, "4915208775497@s.whatsapp.net").setValue(1, "WhatsApp").setValue(2, "Video call +49 1520 8775497");

        contact.addAppendix(name).addAppendix(phone1).addAppendix(phone2);
//        contact.addAppendix(name);
//        contact.addAppendix(w1).addAppendix(w2).addAppendix(w3);
//        contact.addAppendix(phone1);
//        Contact contact = new Contact();
//        ContactAppendix name = new ContactAppendix();
//        name.getMimeType().ignoreSetListener().v("vnd.android.cursor.item/name");
//        name.setValue(0, "Andreas Deibel").setValue(1, "Andreas").setValue(2, "Deibel");
//        ContactAppendix phone1 = new ContactAppendix();
//        phone1.getMimeType().ignoreSetListener().v("vnd.android.cursor.item/phone_v2");
//        phone1.setValue(0, "015-208-775497").setValue(1, "2");
//        ContactAppendix phone2 = new ContactAppendix();
//        phone2.getMimeType().ignoreSetListener().v("vnd.android.cursor.item/phone_v2");
//        phone2.setValue(0, "015-208-775497").setValue(1, "2");
//        contact.addAppendix(name).addAppendix(phone1).addAppendix(phone2);
        contact.hash();
        Lok.debug("ContactAppendix.main");

    }

    @Override
    protected void init() {
        mimeType.setSetListener(mime -> {
            if (mime != null) {
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
                dataColMap.put("data" + i, pair);
            }
            insertAttributes = newInserts;
        } else {
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
        if (noOfColumns < 15)
            hashPairs.add(blob);
        hashPairs.add(mimeType);
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
        Integer noOfColumns = null;
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

package mein.de.contacts.data.db;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/23/17.
 */

public abstract class ContactAppendix extends SQLTableObject implements SerializableEntity {

    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<Long> contactId = new Pair<>(Long.class, "contactid");
    @JsonIgnore
    private Pair<Long> androidId = new Pair<>(Long.class, "aid");

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
}

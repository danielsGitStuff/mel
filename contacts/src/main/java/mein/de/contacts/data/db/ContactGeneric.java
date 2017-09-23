package mein.de.contacts.data.db;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/23/17.
 */

public abstract  class ContactGeneric extends SQLTableObject implements SerializableEntity{

    private Pair<Long> id = new Pair<>(Long.class, "id");

    @Override
    protected void init() {
        populateInsert();
        for (int i = 1; i < 16; i++) {
            String col = "data" + i;
            insertAttributes.add(new Pair<String>(String.class, col));
        }
        populateAll(id);
    }

    public void setValue(int index, String value){
        insertAttributes.get(index).setValueUnsecure(value);
    }
}

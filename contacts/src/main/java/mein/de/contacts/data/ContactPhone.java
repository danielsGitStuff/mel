package mein.de.contacts.data;

import java.nio.channels.Pipe;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/22/17.
 */

public class ContactPhone extends SQLTableObject implements SerializableEntity {

    private Pair<Long> id = new Pair<>(Long.class, "id");

    public ContactPhone(){
        init();
    }

    @Override
    public String getTableName() {
        return "contactPhone";
    }

    @Override
    protected void init() {
        populateInsert();
        for (int i = 1; i < 16; i++) {
            String col = "data" + i;
            insertAttributes.add(new Pair<String>(String.class, col));
        }
        populateAll(id);
    }

    public ContactPhone setValue(int index, String value){
        insertAttributes.get(index).setValueUnsecure(value);
        return this;
    }
}

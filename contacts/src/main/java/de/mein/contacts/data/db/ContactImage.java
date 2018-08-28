package de.mein.contacts.data.db;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 10/2/17.
 */

public class ContactImage extends SQLTableObject implements SerializableEntity {

    public ContactImage(){

    }


    @Override
    public String getTableName() {
        return "image";
    }

    @Override
    protected void init() {

    }
}

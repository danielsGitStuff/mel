package de.mel.contacts.data.db;

import de.mel.core.serialize.SerializableEntity;
import de.mel.sql.SQLTableObject;

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

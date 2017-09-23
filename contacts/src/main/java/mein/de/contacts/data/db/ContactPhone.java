package mein.de.contacts.data.db;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/22/17.
 */

public class ContactPhone extends ContactGeneric {


    public ContactPhone(){
        init();
    }

    @Override
    public String getTableName() {
        return "contactPhone";
    }



}

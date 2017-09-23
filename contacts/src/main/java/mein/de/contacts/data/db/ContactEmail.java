package mein.de.contacts.data.db;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 9/23/17.
 */

public class ContactEmail extends ContactGeneric{

    public ContactEmail(){
        init();
    }
    @Override
    public String getTableName() {
        return "contactemail";
    }

}

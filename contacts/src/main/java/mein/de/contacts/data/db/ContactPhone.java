package mein.de.contacts.data.db;

/**
 * Created by xor on 9/22/17.
 */

public class ContactPhone extends ContactAppendix {

    public ContactPhone(){
        init();
    }

    @Override
    public String getTableName() {
        return "phone";
    }



}

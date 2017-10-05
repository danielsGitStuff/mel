package de.mein.contacts.data.db;

/**
 * Created by xor on 9/23/17.
 */

public class ContactEmail extends ContactAppendix {

    public ContactEmail(){
        init();
    }
    @Override
    public String getTableName() {
        return "email";
    }

}

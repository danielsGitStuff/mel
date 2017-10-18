package de.mein.contacts.data;

import de.mein.contacts.data.db.Contact;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 10/18/17.
 */

public class ContactJoinDummy extends SQLTableObject {
    private Contact dummy = new Contact();
    private Pair<Long> id = dummy.getId();
    private Pair<String> name = new Pair<String>(String.class,"name");

    public ContactJoinDummy() {
        init();
    }

    @Override
    public String getTableName() {
        return dummy.getTableName();
    }

    @Override
    protected void init() {
        populateInsert();
        populateAll(id,name);
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<String> getName() {
        return name;
    }

}

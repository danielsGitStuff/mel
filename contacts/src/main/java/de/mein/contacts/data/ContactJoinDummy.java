package de.mein.contacts.data;

import de.mein.contacts.data.db.Contact;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 10/18/17.
 */

public class ContactJoinDummy extends SQLTableObject {
    private Pair<Long> leftId = new Pair<>(Long.class,"loid");
    private Pair<Long> rightId = new Pair<>(Long.class,"reid");
    private Pair<String> name = new Pair<>(String.class,"name");

    public ContactJoinDummy() {
        init();
    }

    @Override
    public String getTableName() {
        return new Contact().getTableName();
    }

    @Override
    protected void init() {
        populateInsert();
        populateAll(name,leftId,rightId);
    }


    public Pair<String> getName() {
        return name;
    }

    public Pair<Long> getLeftId() {
        return leftId;
    }

    public Pair<Long> getRightId() {
        return rightId;
    }

    public boolean both() {
        return leftId.notNull() && rightId.notNull();
    }
}

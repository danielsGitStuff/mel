package de.mein.contacts.data.db;

import de.mein.sql.Pair;

/**
 * Created by xor on 10/6/17.
 */

public class ContactStructuredName extends ContactAppendix {
    public ContactStructuredName() {
        init();
    }
//
//    private Pair<String> givenName = new Pair<>(String.class, "given");
//    private Pair<String> familyName = new Pair<>(String.class, "family");
//    private Pair<String> middleName = new Pair<>(String.class, "middle");

    @Override
    public String getTableName() {
        return "name";
    }
}

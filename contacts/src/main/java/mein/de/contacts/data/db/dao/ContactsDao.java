package mein.de.contacts.data.db.dao;

import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.db.Contact;

/**
 * Created by xor on 9/23/17.
 */

public class ContactsDao extends Dao {
    public ContactsDao(ISQLQueries sqlQueries) {
        super(sqlQueries);
    }

    public ContactsDao(ISQLQueries sqlQueries, boolean lock) {
        super(sqlQueries, lock);
    }

    public void insert(Contact contact) throws SqlQueriesException {
        Long id = sqlQueries.insert(contact);
        contact.getId().v(id);
    }
}

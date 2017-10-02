package mein.de.contacts.data.db.dao;

import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.db.Contact;
import mein.de.contacts.data.db.ContactEmail;
import mein.de.contacts.data.db.ContactPhone;

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

    public void deleteAll() throws SqlQueriesException {
        sqlQueries.delete(new Contact(), null, null);
    }

    public void insert(Contact contact) throws SqlQueriesException {
        final Long contactId = sqlQueries.insert(contact);
        contact.getId().v(contactId);
        for (ContactEmail email : contact.getEmails()) {
            email.getContactId().v(contactId);
            insetEmail(email);
        }
        for (ContactPhone phone : contact.getPhones()) {
            phone.getContactId().v(contactId);
            insertPhone(phone);
        }
    }

    private void insertPhone(ContactPhone phone) throws SqlQueriesException {
        Long id = sqlQueries.insert(phone);
        phone.getId().v(id);
    }

    private void insetEmail(ContactEmail email) throws SqlQueriesException {
        Long id = sqlQueries.insert(email);
        email.getId().v(id);
    }

}

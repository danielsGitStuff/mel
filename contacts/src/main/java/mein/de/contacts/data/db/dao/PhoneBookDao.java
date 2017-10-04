package mein.de.contacts.data.db.dao;

import java.util.List;

import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.db.Contact;
import mein.de.contacts.data.db.PhoneBook;

/**
 * Created by xor on 10/4/17.
 */

public class PhoneBookDao extends Dao {
    private final ContactsDao contactsDao;

    public PhoneBookDao(ContactsDao contactsDao, ISQLQueries sqlQueries) {
        super(sqlQueries);
        this.contactsDao = contactsDao;
    }

    public PhoneBook create() throws SqlQueriesException {
        PhoneBook phoneBook = new PhoneBook();
        Long id = sqlQueries.insert(phoneBook);
        phoneBook.getId().v(id);
        return phoneBook;
    }

    /**
     * @param id
     * @return PhoneBook that does not provide any {@link Contact}s.
     * @throws SqlQueriesException
     */
    public PhoneBook loadFlatPhoneBook(Long id) throws SqlQueriesException {
        PhoneBook dummy = new PhoneBook();
        String where = dummy.getId().k() + "=?";
        return sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(id), PhoneBook.class);
    }

    /**
     * @param id
     * @return PhoneBook with {@link Contact}s attached.
     * @throws SqlQueriesException
     */
    public PhoneBook loadPhoneBook(Long id) throws SqlQueriesException {
        PhoneBook phoneBook = loadFlatPhoneBook(id);
        List<Contact> contacts = contactsDao.getContacts(id);
        phoneBook.setContacts(contacts);
        phoneBook.deepHash();
        return phoneBook;
    }

    public void updateFlat(PhoneBook phoneBook) throws SqlQueriesException {
        sqlQueries.update(phoneBook, phoneBook.getId().k() + "=?", ISQLQueries.whereArgs(phoneBook.getId().v()));
    }
}

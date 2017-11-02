package de.mein.contacts.data.db.dao;

import java.util.List;

import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 10/4/17.
 */

public class PhoneBookDao extends Dao {
    private final ContactsDao contactsDao;

    public PhoneBookDao(ContactsDao contactsDao, ISQLQueries sqlQueries) {
        super(sqlQueries);
        this.contactsDao = contactsDao;
    }

    public PhoneBook create(long version) throws SqlQueriesException {
        PhoneBook phoneBook = new PhoneBook();
        phoneBook.getCreated().v(System.currentTimeMillis());
        phoneBook.getVersion().v(version);
        Long id = sqlQueries.insert(phoneBook);
        phoneBook.getId().v(id);
        System.out.println("PhoneBookDao.create.id=" + id + ",version=" + version);
        return phoneBook;
    }

    /**
     * Inserts {@link PhoneBook} and all its {@link Contact}s.<br>
     * Nothing more ;)
     *
     * @param phoneBook
     */
    public void insertDeep(PhoneBook phoneBook) throws SqlQueriesException {
        Long phoneBookId = sqlQueries.insert(phoneBook);
        phoneBook.getId().v(phoneBookId);
        for (Contact contact : phoneBook.getContacts()) {
            contact.getPhonebookId().v(phoneBookId);
            contactsDao.insert(contact);
        }
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
        phoneBook.hash();
        return phoneBook;
    }

    /**
     * @param id
     * @return PhoneBook with {@link Contact}s attached.
     * @throws SqlQueriesException
     */
    public PhoneBook loadDeepPhoneBook(Long id) throws SqlQueriesException {
        PhoneBook phoneBook = loadPhoneBook(id);
        List<Contact> contacts = contactsDao.getDeepContacts(id);
        phoneBook.setContacts(contacts);
        //phoneBook.hash();
        return phoneBook;
    }

    public void updateFlat(PhoneBook phoneBook) throws SqlQueriesException {
        sqlQueries.update(phoneBook, phoneBook.getId().k() + "=?", ISQLQueries.whereArgs(phoneBook.getId().v()));
    }

    public void deletePhoneBook(Long id) throws SqlQueriesException {
        PhoneBook phoneBook = new PhoneBook();
        sqlQueries.delete(phoneBook, phoneBook.getId().k() + "=?", ISQLQueries.whereArgs(id));
    }
}

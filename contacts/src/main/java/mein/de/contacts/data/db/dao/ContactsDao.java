package mein.de.contacts.data.db.dao;

import java.util.List;

import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.PhoneBook;
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
            insertEmail(email);
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

    private void insertEmail(ContactEmail email) throws SqlQueriesException {
        Long id = sqlQueries.insert(email);
        email.getId().v(id);
    }

    public List<Contact> getContacts() throws SqlQueriesException {
        List<Contact> contacts;
        Contact contact = new Contact();
        contacts = sqlQueries.load(contact.getAllAttributes(), contact, null, null);
        return contacts;
    }

    public PhoneBook getPhoneBook() throws SqlQueriesException {
        PhoneBook phoneBook = new PhoneBook();
        phoneBook.setContacts(getContacts());
        return phoneBook;
    }

    public Contact getContactByAndroidId(Long androidId) throws SqlQueriesException {
        Contact contact = new Contact();
        String where = contact.getAndroidId().k() + "=?";
        List<Contact> contacts = sqlQueries.load(contact.getAllAttributes(), contact, where, ISQLQueries.whereArgs(androidId));
        if (contacts.size() > 0)
            return contacts.get(0);
        return null;
    }

    public void update(Contact contact) throws SqlQueriesException {
        ContactPhone phoneDummy = new ContactPhone();
        ContactEmail emailDummy = new ContactEmail();
        sqlQueries.execute("delete from " + phoneDummy.getTableName() + " where " + phoneDummy.getContactId().k() + "=?", ISQLQueries.whereArgs(contact.getId().v()));
        sqlQueries.execute("delete from " + emailDummy.getTableName() + " where " + emailDummy.getContactId().k() + "=?", ISQLQueries.whereArgs(contact.getId().v()));
        for (ContactPhone phone : contact.getPhones()) {
            phone.getId().nul();
            phone.getContactId().v(contact.getId().v());
            insertPhone(phone);
        }
        for (ContactEmail email : contact.getEmails()) {
            email.getId().nul();
            email.getContactId().v(contact.getId().v());
            insertEmail(email);
        }
    }
}

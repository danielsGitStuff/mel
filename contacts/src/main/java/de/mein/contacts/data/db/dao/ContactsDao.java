package de.mein.contacts.data.db.dao;

import java.util.List;

import de.mein.contacts.data.db.ContactAppendix;
import de.mein.contacts.data.db.ContactStructuredName;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;
import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.ContactEmail;
import de.mein.contacts.data.db.ContactPhone;

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
        insertAppendices(contact);
    }

    private void insertAppendices(Contact contact) throws SqlQueriesException {
        insertAppendices(contact.getId().v(), contact.getAppendices());
    }

    private void insertAppendices(Long contactId, List<? extends ContactAppendix> appendices) throws SqlQueriesException {
        for (ContactAppendix appendix : appendices) {
            appendix.getContactId().v(contactId);
            insertAppendix(appendix);
        }
    }

    private void insertAppendix(ContactAppendix appendix) throws SqlQueriesException {
        Long id = sqlQueries.insert(appendix);
        appendix.getId().v(id);
    }

    public List<Contact> getContacts(Long phoneBookId) throws SqlQueriesException {
        List<Contact> contacts;
        Contact contact = new Contact();
        String where = contact.getPhonebookId().k() + "=?";
        contacts = sqlQueries.load(contact.getAllAttributes(), contact, where, ISQLQueries.whereArgs(phoneBookId));
        return contacts;
    }

    public List<Contact> getDeepContacts(Long phoneBookId) throws SqlQueriesException {
        List<Contact> contacts;
        Contact dummy = new Contact();
        String where = dummy.getPhonebookId().k() + "=?";
        contacts = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(phoneBookId));
        for (Contact contact : contacts) {
            contact.setAppendices(loadAppendices(contact.getId().v(), new ContactAppendix()));
        }
        return contacts;
    }

    private <T extends ContactAppendix> List<T> loadAppendices(Long contactId, T appendix) throws SqlQueriesException {
        return sqlQueries.load(appendix.getAllAttributes(), appendix, appendix.getContactId().k() + "=?", ISQLQueries.whereArgs(contactId));
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
        for (ContactAppendix phone : contact.getAppendices()) {
            phone.getId().nul();
            phone.getContactId().v(contact.getId().v());
            insertAppendix(phone);
        }

    }

    public <T extends ContactAppendix> List<T> getAppendix(Long contactId, Class<T> clazz) throws SqlQueriesException, IllegalAccessException, InstantiationException {
        ContactAppendix appendix = clazz.newInstance();
        return sqlQueries.load(appendix.getAllAttributes(), (T) appendix, appendix.getContactId().k() + "=?", ISQLQueries.whereArgs(contactId));
    }

    public void updateHash(Contact contact) throws SqlQueriesException {
        String stmt = "update " + contact.getTableName() + " set " + contact.getHash().k() + "=? where " + contact.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(contact.getHash().v(), contact.getId().v()));
    }
}

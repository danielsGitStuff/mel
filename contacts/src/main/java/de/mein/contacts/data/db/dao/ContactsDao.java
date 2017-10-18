package de.mein.contacts.data.db.dao;

import java.util.ArrayList;
import java.util.List;

import de.mein.contacts.data.db.AppendixWrapper;
import de.mein.contacts.data.db.ContactAppendix;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.ISQLResource;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;
import de.mein.contacts.data.db.Contact;

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

    public void getIdsForConflict(Long localPhoneBookId, Long receivedPhoneBookId, String nameMimeColName, String nameColumnName) {
        Contact cd = new Contact();
        ContactAppendix ad = new ContactAppendix();
        String query = "select case when (reid is null) then loid else reid end as " + cd.getId().k() + ", lo." + nameColumnName + " as  from (select cc." + cd.getId().k() + " as loid, aa." + nameColumnName + " from " + cd.getTableName() + " cc ,  " + ad.getTableName() + "  aa on  cc." + cd.getId().k() + " = aa." + ad.getContactId().k() + " where aa." + ad.getMimeType().k() + "=?  and cc." + cd.getPhonebookId().k() + "=?) lo left join\n" +
                "(select cc.id as reid,aa." + nameColumnName + " from  " + cd.getTableName() + " cc ,  " + ad.getTableName() + "  aa on  cc." + cd.getId().k() + " = aa." + ad.getContactId().k() + " where aa." + ad.getMimeType().k() + "=?  and cc." + cd.getPhonebookId().k() + "=? )  re on lo." + nameColumnName + " = re." + nameColumnName + ";";
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


    public List<ContactAppendix> getAppendices(Long contactId) throws SqlQueriesException, IllegalAccessException, InstantiationException {
        ContactAppendix dummy = new ContactAppendix();
        String where = dummy.getContactId().k() + "=?";
        return sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(contactId));
    }

    public <T extends AppendixWrapper> List<T> getWrappedAppendices(Long contactId, Class<T> appendixClass) throws SqlQueriesException, IllegalAccessException, InstantiationException {
        AppendixWrapper dummyWrapper = appendixClass.newInstance();
        ContactAppendix dummy = new ContactAppendix();
        String where = dummy.getContactId().k() + "=? and " + dummy.getMimeType().k() + "=?";
        List<ContactAppendix> appendices = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(contactId, dummyWrapper.getMimeType()));
        List<T> result = new ArrayList<>(appendices.size());
        for (ContactAppendix appendix : appendices) {
            T wrapper = (T) appendixClass.newInstance().setAppendix(appendix);
            result.add(wrapper);
        }
        return result;
    }

    public void updateHash(Contact contact) throws SqlQueriesException {
        String stmt = "update " + contact.getTableName() + " set " + contact.getHash().k() + "=? where " + contact.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(contact.getHash().v(), contact.getId().v()));
    }

    public ISQLResource<Contact> contactsResource(Long phoneBookId) throws SqlQueriesException {
        Contact contact = new Contact();
        String where = contact.getPhonebookId().k() + "=?";
        return sqlQueries.loadResource(contact.getAllAttributes(), Contact.class, where, ISQLQueries.whereArgs(phoneBookId));
    }

    public Contact getContactByName(String name, String structuredNameMimeType, String nameColumnName) throws SqlQueriesException {
        Contact contactDummy = new Contact();
        ContactAppendix dummyAppendix = new ContactAppendix();
        //select * from contacts c, appendix a on c.id=a.contactid where a.data1="tim müller";
        String query = "select " + contactDummy.getId().k() + " from " + contactDummy.getTableName() + " c, " + dummyAppendix.getTableName() + " a on c."
                + contactDummy.getId().k() + "=" + dummyAppendix.getContactId().k() + " where a." + nameColumnName + "=? and a." + dummyAppendix.getMimeType().k() + "=?";
        Long contactId = sqlQueries.queryValue(query, Long.class, ISQLQueries.whereArgs(name, structuredNameMimeType));
        String where = contactDummy.getId().k() + "=?";
        Contact contact = sqlQueries.loadFirstRow(contactDummy.getAllAttributes(), contactDummy, where, ISQLQueries.whereArgs(contactId), Contact.class);
        return contact;
    }

    public void updateChecked(Contact contact) throws SqlQueriesException {
        String stmt = "update " + contact.getTableName() + " set " + contact.getChecked().k() + "=? where " + contact.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(contact.getChecked().v(), contact.getId().v()));
    }

    public ISQLResource<Contact> contactsResource(Long phoneBookId, boolean flagChecked) throws SqlQueriesException {
        Contact contact = new Contact();
        String where = contact.getPhonebookId().k() + "=? and " + contact.getChecked().k() + "=?";
        return sqlQueries.loadResource(contact.getAllAttributes(), Contact.class, where, ISQLQueries.whereArgs(phoneBookId, flagChecked));
    }
}

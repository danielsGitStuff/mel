package de.mein.contacts.data.db.dao;

import java.util.ArrayList;
import java.util.List;

import de.mein.contacts.data.ContactJoinDummy;
import de.mein.contacts.data.db.AppendixWrapper;
import de.mein.contacts.data.db.ContactAppendix;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.ISQLResource;
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

    public ISQLResource<ContactJoinDummy> getDummiesForConflict(Long localPhoneBookId, Long receivedPhoneBookId, String nameMimeType, String nameColumnName) throws SqlQueriesException {
//        select case when(lo.name is null) then re.name else lo.name end as name, lo.id as loid, re.id as reid from
//                (select c.id, a.data1 as name from contacts c, appendix a on c.id=a.contactid where a.mime="vnd.android.cursor.item/name" and c.pid=1) lo
//        left join
//        (select c.id, a.data1 as name from contacts c, appendix a on c.id=a.contactid where a.mime="vnd.android.cursor.item/name" and c.pid=2) re
//        on re.name=lo.name
//        union
//        select a.data1 as name, null as loid, c.id as reid from contacts c, appendix a on c.id=a.contactid
//        where a.mime="vnd.android.cursor.item/name" and c.pid=2
//        and not exists
//                (select 1 from contacts c, appendix a on c.id=a.contactid where a.mime="vnd.android.cursor.item/name" and c.pid=1 and a.data1=name)

        Contact cd = new Contact();
        ContactAppendix ad = new ContactAppendix();
        ContactJoinDummy jd = new ContactJoinDummy();
        String query = "select case when(lo.name is null) then re.name else lo.name end as name, lo." + cd.getId().k() + " as loid, re." + cd.getId().k() + " as reid from \n" +
                "(select c." + cd.getId().k() + ", a." + nameColumnName + " as name from " + cd.getTableName() + " c, " + ad.getTableName() + " a on c." + cd.getId().k() + "=a." + ad.getContactId().k() + " where a." + ad.getMimeType().k() + "=? and c." + cd.getPhonebookId().k() + "=?) lo\n" +
                "left join\n" +
                "(select c." + cd.getId().k() + ", a." + nameColumnName + " as name from " + cd.getTableName() + " c, " + ad.getTableName() + " a on c." + cd.getId().k() + "=a." + ad.getContactId().k() + " where a." + ad.getMimeType().k() + "=? and c." + cd.getPhonebookId().k() + "=?) re \n" +
                "on re.name=lo.name\n" +
                "union \n" +
                "select a." + nameColumnName + " as name, null as loid, c." + cd.getId().k() + " as reid from " + cd.getTableName() + " c, " + ad.getTableName() + " a on c." + cd.getId().k() + "=a." + ad.getContactId().k() + " \n" +
                "where a." + ad.getMimeType().k() + "=? and c." + cd.getPhonebookId().k() + "=?\n" +
                "and not exists \n" +
                "(select 1 from " + cd.getTableName() + " c, " + ad.getTableName() + " a on c." + cd.getId().k() + "=a." + ad.getContactId().k() + " where a." + ad.getMimeType().k() + "=? and c." + cd.getPhonebookId().k() + "=? and a." + nameColumnName + "=name)";
        return sqlQueries.loadQueryResource(query, jd.getAllAttributes(), ContactJoinDummy.class, ISQLQueries.whereArgs(nameMimeType, localPhoneBookId, nameMimeType, receivedPhoneBookId, nameMimeType, receivedPhoneBookId,nameMimeType,localPhoneBookId));
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

    public Contact getContactByName(Long phoneBookId, String name, String structuredNameMimeType, String nameColumnName) throws SqlQueriesException {
        Contact contactDummy = new Contact();
        ContactAppendix dummyAppendix = new ContactAppendix();
        //select * from contacts c, appendix a on c.id=a.contactid where a.data1="tim müller";
        String query = "select c." + contactDummy.getId().k() + " from " + contactDummy.getTableName() + " c, " + dummyAppendix.getTableName() + " a on c."
                + contactDummy.getId().k() + "=" + dummyAppendix.getContactId().k() + " where a." + nameColumnName + "=? and a." + dummyAppendix.getMimeType().k() + "=?"
                + " and c." + contactDummy.getPhonebookId().k() + "=?";
        Long contactId = sqlQueries.queryValue(query, Long.class, ISQLQueries.whereArgs(name, structuredNameMimeType, phoneBookId));
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

package de.mel.contacts;

import de.mel.contacts.data.db.ContactsDatabaseManager;

/**
 * Created by xor on 9/23/17.
 */

public class ContactsInjector {
    public static void setDriveSqlInputStreamInjector(ContactsDatabaseManager.ContactsSqlInputStreamInjector contactsSqlInputStreamInjector) {
        ContactsDatabaseManager.setContactsSqlInputStreamInjector(contactsSqlInputStreamInjector);
    }

    public static void setSqlConnectionCreator(ContactsDatabaseManager.SQLConnectionCreator connectionCreator) {
        ContactsDatabaseManager.setSqlqueriesCreator(connectionCreator);
    }
}

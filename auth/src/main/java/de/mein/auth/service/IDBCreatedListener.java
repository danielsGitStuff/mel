package de.mein.auth.service;

import de.mein.auth.data.access.DatabaseManager;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 4/27/16.
 */
public interface IDBCreatedListener {
    void onDBcreated(DatabaseManager databaseManager) throws SqlQueriesException;
}

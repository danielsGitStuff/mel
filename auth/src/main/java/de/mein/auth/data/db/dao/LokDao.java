package de.mein.auth.data.db.dao;

import de.mein.auth.data.db.LokEntry;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;

public class LokDao extends Dao {
    public LokDao(ISQLQueries sqlQueries) {
        super(sqlQueries);
    }

    public void insert(LokEntry lokEntry) throws SqlQueriesException {
        Long id = sqlQueries.insert(lokEntry);
        lokEntry.getId().v(id);
    }
}

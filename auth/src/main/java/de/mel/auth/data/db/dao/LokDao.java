package de.mel.auth.data.db.dao;

import de.mel.auth.data.db.LokEntry;
import de.mel.sql.Dao;
import de.mel.sql.ISQLQueries;
import de.mel.sql.SqlQueriesException;

public class LokDao extends Dao {
    public LokDao(ISQLQueries sqlQueries) {
        super(sqlQueries);
    }

    public void insert(LokEntry lokEntry) throws SqlQueriesException {
        Long id = sqlQueries.insert(lokEntry);
        lokEntry.getId().v(id);
    }
}

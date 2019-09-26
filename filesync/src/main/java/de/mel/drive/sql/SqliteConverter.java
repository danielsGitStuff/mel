package de.mel.drive.sql;

import de.mel.sql.SqlQueriesException;

public class SqliteConverter {
    public static Boolean intToBoolean(Integer result) throws SqlQueriesException {
        try {
            if (result == 1)
                return true;
            if (result == 0)
                return false;
        } catch (Exception e) {
            throw new SqlQueriesException(SqliteConverter.class + ".intToBoolean(): something went horribly wrong converting the database result: " + (result == null ? "null" : result.getClass() + ": " + result));
        }
        return null;
    }
}

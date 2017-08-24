package de.mein.drive.sql;

import de.mein.sql.SqlQueriesException;

public class SqliteConverter {
    public static Boolean intToBoolean(Integer result) throws SqlQueriesException{
        if (result == null)
            return false;
        if (result == 1)
            return true;
        if (result == 0)
            return false;
        throw new SqlQueriesException(SqliteConverter.class + ".intToBoolean(): something went horribly wrong converting the database result: " + (result == null ? "null" : result.getClass() + ": " + result));
    }
}

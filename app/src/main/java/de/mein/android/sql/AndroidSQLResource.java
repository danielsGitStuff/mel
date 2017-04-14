package de.mein.android.sql;

import android.database.Cursor;

import java.sql.SQLException;
import java.util.List;

import de.mein.sql.ISQLResource;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 3/22/17.
 */

public class AndroidSQLResource<T extends SQLTableObject> implements ISQLResource {
    private final Class<T> clazz;
    private final Cursor cursor;

    public AndroidSQLResource(Cursor cursor, Class<T> clazz) {
        this.clazz = clazz;
        this.cursor = cursor;
    }

    @Override
    public SQLTableObject getNext() throws SqlQueriesException {
        T sqlTable = null;
        try {
            if (cursor.moveToNext()) {
                sqlTable = clazz.newInstance();
                List<Pair<?>> attributes = sqlTable.getAllAttributes();
                for (Pair<?> pair : attributes) {
                    try {
                        AndroidSQLQueries.readCursorToPair(cursor, pair);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }catch (Exception e){
            throw new SqlQueriesException(e);
        }
        return sqlTable;
    }

    @Override
    public void close() throws SQLException {
        cursor.close();
    }
}
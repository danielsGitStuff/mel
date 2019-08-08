package de.mein.android.sql;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.os.Build;

import java.sql.SQLException;
import java.util.List;

import de.mein.Lok;
import de.mein.sql.ISQLResource;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 3/22/17.
 */

public class AndroidSQLResource<T extends SQLTableObject> implements ISQLResource {
    private final Class<T> clazz;
    private final SQLiteCursor cursor;
    private int countdown = 500;
    private int count = 0;

    public AndroidSQLResource(SQLiteCursor cursor, Class<T> clazz) {
        this.clazz = clazz;
        this.cursor = cursor;
    }

    @Override
    public SQLTableObject getNext() throws SqlQueriesException {
        T sqlTable = null;
        try {
            count++;
            if (cursor.getWindow() != null) {
                countdown--;
                cursor.getWindow().freeLastRow();
                if (countdown == 0) {
                    cursor.getWindow().clear();
                    countdown = 500;
                }
            }
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
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
        return sqlTable;
    }

    @Override
    public void close() throws SqlQueriesException {
        cursor.close();
    }

    @Override
    public boolean isClosed() throws SqlQueriesException {
        return cursor.isClosed();
    }
}

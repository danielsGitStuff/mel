package de.mein.android.sql;

import android.database.Cursor;

import java.sql.SQLException;

import de.mein.sql.conn.SQLResultSet;

/**
 * Created by xor on 2/6/17.
 */
public class AndroidResultSet extends SQLResultSet {
    private final Cursor cursor;

    public AndroidResultSet(Cursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public boolean next() throws SQLException {
        return false;
    }

    @Override
    public int getRow() throws SQLException {
        return 0;
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return false;
    }

    @Override
    public Object getObject(String k) throws SQLException {
        return null;
    }

    public void close() {
        cursor.close();
    }

    @Override
    public Object getObject(int i) throws SQLException {
        return null;
    }

    @Override
    public String[] getColumns() throws SQLException {
        return new String[0];
    }
}

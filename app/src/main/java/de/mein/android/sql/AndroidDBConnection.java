package de.mein.android.sql;

import android.database.sqlite.SQLiteDatabase;

import java.sql.SQLException;

import de.mein.sql.SQLStatement;
import de.mein.sql.conn.SQLConnection;

/**
 * Created by xor on 2/6/17.
 */
public class AndroidDBConnection extends SQLConnection{
    private final SQLiteDatabase db;

    public AndroidDBConnection(SQLiteDatabase writableDatabase) {
        this.db = writableDatabase;
    }

    public SQLiteDatabase getDb() {
        return db;
    }

    @Override
    public SQLStatement prepareStatement(String query) throws SQLException {
        return new AndroiDBStatement(db,query);
    }

    @Override
    public SQLStatement prepareStatement(String query, int returnGeneratedKeys) throws SQLException {
        return null;
    }
}

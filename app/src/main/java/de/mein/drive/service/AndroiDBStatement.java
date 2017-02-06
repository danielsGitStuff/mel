package de.mein.drive.service;

import android.database.sqlite.SQLiteDatabase;

import java.sql.SQLException;

import de.mein.sql.SQLStatement;
import de.mein.sql.con.SQLResultSet;

/**
 * Created by xor on 2/6/17.
 */
public class AndroiDBStatement extends SQLStatement {
    private final SQLiteDatabase db;
    private AndroidResultSet androidResultSet;

    public AndroiDBStatement(SQLiteDatabase db, String query) {
        this.db = db;
        this.query = query;
    }

    @Override
    public SQLResultSet getResultSet() throws SQLException {
        return androidResultSet;
    }

    @Override
    public SQLResultSet getGeneratedKeys() throws SQLException {
        return null;
    }

    @Override
    public void setObject(int pos, Object o) throws SQLException {
        System.out.println("AndroiDBStatement.setObject:NOT:IMPLEMENTED");
    }

    @Override
    public void close() throws SQLException {
        if (androidResultSet != null)
            androidResultSet.close();
    }

    @Override
    public int executeUpdate() throws SQLException {
        return 0;
    }

    @Override
    public Object execute() throws SQLException {
        //this.androidResultSet = new AndroidResultSet(db.rawQuery(query, null));
        db.execSQL(query);
        return null;
    }

    @Override
    public boolean execute(String statement) throws SQLException {
        return false;
    }
}

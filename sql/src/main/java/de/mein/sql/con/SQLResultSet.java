package de.mein.sql.con;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Created by xor on 2/5/17.
 */

public abstract class SQLResultSet {
    public abstract boolean next() throws SQLException;

    public abstract int getRow() throws SQLException;

    public abstract boolean isAfterLast() throws SQLException;

    public abstract Object getObject(String k) throws SQLException;

    public abstract void close() throws SQLException;

    public abstract Object getObject(int i) throws SQLException;

    public abstract String[] getColumns() throws SQLException;
}

package de.mein.sql;

import de.mein.sql.conn.SQLResultSet;

import java.sql.SQLException;

/**
 * Created by xor on 2/3/17.
 */
public abstract class SQLStatement {
    protected SQLResultSet resultSet;
    protected SQLResultSet generatedKeys;
    protected String query;

    public abstract SQLResultSet getResultSet() throws SQLException;

    public abstract SQLResultSet getGeneratedKeys() throws SQLException;

    public abstract void setObject(int pos, Object o) throws SQLException;

    public abstract void close() throws SQLException;

    public abstract int executeUpdate() throws SQLException;

    public abstract Object execute() throws SQLException;

    public abstract boolean execute(String statement) throws SQLException;
}

package de.mein.sql.con;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import de.mein.sql.SQLStatement;

/**
 * Created by xor on 2/3/17.
 */
public abstract class SQLConnection {
    private DatabaseMetaData metaData;

    public abstract SQLStatement prepareStatement(String query) throws SQLException;

    public abstract SQLStatement prepareStatement(String query, int returnGeneratedKeys) throws SQLException;

    public void setAutoCommit(boolean b) {

    }

    public void commit() {

    }

    public void rollback() {

    }

    public DatabaseMetaData getMetaData() {
        return metaData;
    }
}

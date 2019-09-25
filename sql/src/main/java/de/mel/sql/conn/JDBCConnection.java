package de.mel.sql.conn;

import java.sql.Connection;
import java.sql.SQLException;

import de.mel.sql.SQLStatement;

/**
 * Created by xor on 2/3/17.
 */

public class JDBCConnection extends SQLConnection {
    private final Connection connection;

    public JDBCConnection(Connection connection) {
        this.connection = connection;
    }

    @Override
    public SQLStatement prepareStatement(String query) throws SQLException {
        return new JDBCStatement(connection.prepareStatement(query));
    }


    @Override
    public SQLStatement prepareStatement(String query, int returnGeneratedKeys) throws SQLException {
        return new JDBCStatement(connection.prepareStatement(query,returnGeneratedKeys));
    }

    public Connection getConnection() {
        return connection;
    }
}

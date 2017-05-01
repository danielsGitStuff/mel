package de.mein.sql.con;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * opens or stores a connection to a mysql service
 *
 * @author xor
 */
public abstract class SQLConnector {

    public static SQLConnection createConnection(String name) throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + name + ".db");
        JDBCConnection jdbcConnection = new JDBCConnection(connection);
        return jdbcConnection;
        //Class.forName("com.mysql.jdbc.Driver");
        //return DriverManager.getConnection("jdbc:mysql://localhost/" + name + "?" + "user=root");
    }

    public static Connection createMysqlRootConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.jdbc.Driver");
        return DriverManager.getConnection("jdbc:mysql://localhost/?user=root&allowMultiQueries=true");
    }

    public static JDBCConnection createSqliteConnection(File file) throws ClassNotFoundException, SQLException {
        Class c = Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        return new JDBCConnection(connection);
    }
    public static Connection createJDBCConnection(File file) throws ClassNotFoundException, SQLException {
        Class c = Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        return connection;
    }

}

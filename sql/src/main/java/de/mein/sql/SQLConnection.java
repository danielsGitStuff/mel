package de.mein.sql;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * opens or stores a connection to a mysql service
 *
 * @author deck006
 */
public abstract class SQLConnection {

    private static Logger logger = Logger.getLogger(SQLConnection.class);

    private static String user;
    private static String pwd;
    private static String host;
    private static String db;
    private static String port;
    private static Connection con = null;
    // private static Integer hash;
    private static String lastErrorMessage;

//	public static String getLastErrorMessage() {
//		return lastErrorMessage;
//	}

    public static Connection createConnection(String name) throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:" + name + ".db");
        //Class.forName("com.mysql.jdbc.Driver");
        //return DriverManager.getConnection("jdbc:mysql://localhost/" + name + "?" + "user=root");
    }

    public static Connection createMysqlRootConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.jdbc.Driver");
        return DriverManager.getConnection("jdbc:mysql://localhost/?user=root&allowMultiQueries=true");
    }

    public static Connection createSqliteConnection(File file) throws ClassNotFoundException, SQLException {
        try {
            DriverManager.registerDriver((Driver) Class.forName("org.sqldroid.SQLDroidDriver").newInstance());
        } catch (Exception e) {
            throw new RuntimeException("Failed to register SQLDroidDriver");
        }
        Class c = Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }


//	public static void close() {
//		if (con != null)
//			try {
//				con.close();
//			} catch (Exception exception) {
//				logger.error("SQLConnection.close()");
//				logger.error("stacktrace", exception);
//			}
//		logger.debug("Connection has a DISCO! nnect");
//	}

    // public static Connection getSQLConnection(Central central) {
    // user = config.getUser();
    // host = config.getHost();
    // pwd = config.getPassword();
    // port = config.getPort();
    // db = config.getDb();
    // try {
    // if (hash != null) {
    // if (hash != config.hashCode()) {
    //
    // con = createConnection();
    //
    // hash = config.hashCode();
    // } else {
    // if (con == null || con.isClosed() == true || con.isValid(3000)==false) {
    // con = createConnection();
    // } else {
    // return con;
    // }
    // }
    // } else {
    // con = createConnection();
    // hash = config.hashCode();
    // }
    //
    // } catch (SQLException exception) {
    // lastErrorMessage = exception.getMessage();
    // logger.debug("SQLConnection.getSQLConnection().fail");
    // logger.debug("SQLException: " + lastErrorMessage);
    // logger.debug("SQLState: " + exception.getSQLState());
    // logger.debug("VendorError: " + exception.getErrorCode());
    // return null;
    // }
    // return con;
    // }

//	private static Connection createConnection() throws SQLException {
//
//		Properties connectionProperties = new Properties();
//		connectionProperties.put("user", user);
//		connectionProperties.put("password", pwd);
//		Connection con = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db,
//				connectionProperties);
//
//		return con;
//	}
}

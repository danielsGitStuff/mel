package de.mein.drive.sql;

import de.mein.sql.SQLQueries;

import java.sql.Connection;

/**
 * Created by xor on 09.07.2016.
 */
public class DriveSqlQueries extends SQLQueries {
    private static Connection connection;
    private static DriveSqlQueries ins;

    public DriveSqlQueries(Connection connection) {
        super(connection);
    }


}

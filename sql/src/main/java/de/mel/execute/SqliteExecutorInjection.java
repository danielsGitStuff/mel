package de.mel.execute;

import java.io.InputStream;

import de.mel.sql.conn.SQLConnection;

/**
 * Created by xor on 3/6/17.
 */

public interface SqliteExecutorInjection {
    void executeStream(SQLConnection connection, InputStream in);
    boolean checkTableExists(SQLConnection connection, String tableName);
}

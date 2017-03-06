package de.mein.execute;

import java.io.InputStream;

import de.mein.sql.con.SQLConnection;

/**
 * Created by xor on 3/6/17.
 */

public interface SqliteExecutorInjection {
    void executeStream(SQLConnection connection, InputStream in);
}

package de.mein.sql;

import java.sql.SQLException;

/**
 * Created by xor on 2/6/17.
 */
public interface ISQLResource<T extends SQLTableObject> extends AutoCloseable {
    T getNext() throws IllegalAccessException, InstantiationException, SQLException;

    @Override
    void close() throws SQLException;
}

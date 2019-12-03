package de.mel.sql;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 2/6/17.
 */
public interface ISQLResource<T extends SQLTableObject> extends AutoCloseable {
    T getNext() throws SqlQueriesException;

    @Override
    void close() throws SqlQueriesException;

    boolean isClosed() throws SqlQueriesException;

    default public List<T> toList() throws SqlQueriesException {
        List<T> list = new ArrayList<>();
        T item = getNext();
        while (item != null) {
            list.add(item);
            item = getNext();
        }
        return list;
    }
}
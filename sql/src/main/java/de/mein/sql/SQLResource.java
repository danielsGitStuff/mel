package de.mein.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;


/**
 * Created by xor on 12/16/16.
 */
public class SQLResource<T extends SQLTableObject> implements ISQLResource<T> {
    private final Class<T> clazz;
    private final PreparedStatement preparedStatement;
    private final List<Pair<?>> columns;
    private ResultSet resultSet;


    public SQLResource(PreparedStatement preparedStatement, Class<T> clazz, List<Pair<?>> columns) throws SQLException {
        this.clazz = clazz;
        this.preparedStatement = preparedStatement;
        this.resultSet = preparedStatement.getResultSet();
        this.columns = columns;
    }


    @Override
    public T getNext() throws SqlQueriesException {
        T sqlTable = null;
        try {
            if (resultSet.next()) {
                sqlTable = clazz.newInstance();
                for (Pair<?> pair : columns) {
                    try {
                        Object res = resultSet.getObject(pair.k());
                        pair.setValueUnsecure(res);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
        return sqlTable;
    }

    @Override
    public void close() throws SqlQueriesException {
        try {
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SqlQueriesException(e);
        }
    }

    @Override
    public boolean isClosed() throws SqlQueriesException {
        try {
            return resultSet.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SqlQueriesException(e);
        }
    }
}

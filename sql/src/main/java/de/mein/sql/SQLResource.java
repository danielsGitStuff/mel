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
    private ResultSet resultSet;


    public SQLResource(PreparedStatement preparedStatement, Class<T> clazz) throws SQLException {
        this.clazz = clazz;
        this.preparedStatement = preparedStatement;
        this.resultSet = preparedStatement.getResultSet();
    }


    @Override
    public T getNext() throws SqlQueriesException {
        T sqlTable = null;
        try {
            if (resultSet.next()) {
                sqlTable = clazz.newInstance();
                List<Pair<?>> attributes = sqlTable.getAllAttributes();
                for (Pair<?> pair : attributes) {
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
}

package de.mein.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by xor on 12/16/16.
 */
public class SQLResource<T extends SQLTableObject> implements AutoCloseable {
    private final Class<T> clazz;
    private Statement statement;
    private ResultSet resultSet;

    public SQLResource(PreparedStatement statement, Class<T> clazz) throws SQLException {
        this.statement = statement;
        this.resultSet = statement.getResultSet();
        this.clazz = clazz;
    }

    public T getNext() throws IllegalAccessException, InstantiationException, SQLException {
        T sqlTable = null;
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
        return sqlTable;
    }

    public void close() throws SQLException {
        resultSet.close();
        statement.close();
    }
}

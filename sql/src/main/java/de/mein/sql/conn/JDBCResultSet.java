package de.mein.sql.conn;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by xor on 2/5/17.
 */

public class JDBCResultSet extends SQLResultSet {
    private ResultSet resultSet;

    public JDBCResultSet(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    @Override
    public boolean next() throws SQLException {
        return resultSet.next();
    }

    @Override
    public int getRow() throws SQLException {
        return resultSet.getRow();
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return resultSet.isAfterLast();
    }

    @Override
    public Object getObject(String k) throws SQLException {
        return resultSet.getObject(k);
    }

    @Override
    public void close() throws SQLException {
        resultSet.close();
    }

    @Override
    public Object getObject(int i) throws SQLException {
        return resultSet.getObject(i);
    }

    @Override
    public String[] getColumns() throws SQLException {
        String[] res = new String[resultSet.getMetaData().getColumnCount()];
        for (int i = 0; i < res.length; i++)
            res[i] = resultSet.getMetaData().getColumnLabel(i + 1);
        return res;
    }



}

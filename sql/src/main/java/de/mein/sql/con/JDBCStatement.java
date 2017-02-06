package de.mein.sql.con;

import de.mein.sql.SQLStatement;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Created by xor on 2/5/17.
 */

public class JDBCStatement extends SQLStatement {
    private PreparedStatement preparedStatement;

    public JDBCStatement(PreparedStatement preparedStatement) {
        this.preparedStatement = preparedStatement;
    }


    @Override
    public SQLResultSet getResultSet() throws SQLException {
        return new JDBCResultSet(preparedStatement.getResultSet());
    }

    @Override
    public SQLResultSet getGeneratedKeys() throws SQLException {
        return new JDBCResultSet(preparedStatement.getGeneratedKeys());
    }

    @Override
    public void setObject(int pos, Object o) throws SQLException {
        preparedStatement.setObject(pos, o);
    }

    @Override
    public void close() throws SQLException {
        preparedStatement.close();
    }

    @Override
    public int executeUpdate() throws SQLException {
        return preparedStatement.executeUpdate();
    }

    @Override
    public Object execute() throws SQLException {
        return preparedStatement.execute();
    }

    @Override
    public boolean execute(String statement) throws SQLException {
        return preparedStatement.execute(statement);
    }
}

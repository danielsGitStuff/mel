package de.mein.sql;


import de.mein.sql.conn.JDBCConnection;
import de.mein.sql.conn.SQLConnection;
import de.mein.sql.transform.SqlResultTransformer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * builds and executes mysql queries for several purposes.
 *
 * @author xor
 */
@SuppressWarnings("Duplicates")
public class SQLQueries extends ISQLQueries {

    private RWLock lock;
    public static final boolean SYSOUT = false;
    private final JDBCConnection sqlConnection;
    private final Connection connection;
    private final SqlResultTransformer resultTransformer;


    public SQLQueries(JDBCConnection connection, SqlResultTransformer resultTransformer) {
        this.sqlConnection = connection;
        this.resultTransformer = resultTransformer;
        this.connection = sqlConnection.getConnection();
    }

    public SQLQueries(JDBCConnection connection, boolean reentrantLockOnWrite, RWLock lock, SqlResultTransformer resultTransformer) {
        this.sqlConnection = connection;
        this.resultTransformer = resultTransformer;
        this.reentrantWriteLock = reentrantLockOnWrite ? new ReentrantLock(true) : null;
        this.connection = sqlConnection.getConnection();
        this.lock = lock;
    }

    @Override
    public SQLConnection getSQLConnection() {
        return sqlConnection;
    }

    @Override
    public void update(SQLTableObject sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        lockWrite();
        out("update()");
        String query;
        List<Pair<?>> what = sqlTableObject.getInsertAttributes();
        String fromTable = sqlTableObject.getTableName();
        query = buildInsertModifyQuery(what, "update", "set", where, fromTable);
        out("update().query= " + query);
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            int count = 1;
            for (Pair<?> attribute : what) {
                pstmt.setObject(count, attribute.v());
                count++;
            }
            if (where != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs, count);
            }
            int changed = pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            System.err.println(e.getSQLState());
            throw new SqlQueriesException(e);
        } finally {
            unlockWrite();
        }
    }

    @Override
    public void delete(SQLTableObject sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        lockWrite();
        String query = "delete from " + sqlTableObject.getTableName();
        if (where != null)
            query += " where " + where;
        out("delete().query= " + query);
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            if (where != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs);
            }
            pstmt.executeUpdate();
            pstmt.close();
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        } finally {
            unlockWrite();
        }
    }


    private void insertArguments(PreparedStatement pstmt, List<Object> whereArgs, int count) throws SQLException {
        for (Object o : whereArgs) {
            pstmt.setObject(count, o);
            count++;
        }
    }


    private void insertArguments(PreparedStatement pstmt, Object[] whereArgs, int count) throws SQLException {
        for (Object o : whereArgs) {
            pstmt.setObject(count, o);
            count++;
        }
    }

    private void insertArguments(PreparedStatement pstmt, List<Object> whereArgs) throws SQLException {
        insertArguments(pstmt, whereArgs, 1);
    }

    @Override
    public <T extends SQLTableObject> ISQLResource<T> loadResource(List<Pair<?>> columns, Class<T> clazz, String where,
                                                                   List<Object> whereArgs) throws SqlQueriesException {
        String selectString = ISQLQueries.buildQueryFrom(columns, clazz, where);
        if (connection == null) {
            return null;
        }
        try {
            PreparedStatement pstmt = connection.prepareStatement(selectString);
            if (where != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs);
            }
            pstmt.execute();
            return new SQLResource<T>(pstmt, clazz);
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
    }


    @Override
    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        return load(columns, sqlTableObject, where, whereArgs, null);
    }

    @Override
    public <T> List<T> loadColumn(Pair<T> column, Class<T> clazz, SQLTableObject sqlTableObject, String tableReference, String where, List<Object> whereArgs, String whatElse) throws SqlQueriesException {
        List<T> result = new ArrayList<>();
        out("load()");
        String fromTable = sqlTableObject.getTableName();
        if (tableReference != null)
            fromTable += " " + tableReference;
        String selectString = buildSelectQuery(new ArrayList<Pair<?>>() {
            {
                add(column);
            }
        }, fromTable);
        if (where != null) {
            selectString += " where " + where;
        }
        if (whatElse != null) {
            selectString += " " + whatElse;
        }
        out(selectString);
        if (connection == null) {
            return null;
        }
        try {
            PreparedStatement pstmt = connection.prepareStatement(selectString);
            if (where != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs);
            }
            pstmt.execute();
            ResultSet resultSet = pstmt.getResultSet();
            boolean hasResult = resultSet.next();
            if (hasResult && resultSet.getRow() > 0) {
                while (!resultSet.isAfterLast()) {
                    try {
                        Object res = resultSet.getObject(column.k());
                        result.add((T) res);
                    } catch (Exception e) {
                        if (!e.getClass().equals(SQLException.class)) {
                            out("load().exception." + e.getClass().toString() + " " + e.getMessage());
                        }
                    }
                    resultSet.next();
                }
            }
            resultSet.close();
            pstmt.close();
            return result;
        } catch (Exception e) {
            System.err.println("SQLQueries.loadColumn.failed.query: " + selectString);
            throw new SqlQueriesException(e);
        }
    }

    @Override
    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> whereArgs, String whatElse) throws SqlQueriesException {
        List<T> result = new ArrayList<>();
        out("load()");
        String fromTable = sqlTableObject.getTableName();
        String selectString = buildSelectQuery(columns, fromTable);
        if (where != null) {
            selectString += " where " + where;
        }
        if (whatElse != null) {
            selectString += " " + whatElse;
        }
        out(selectString);
        if (connection == null) {
            return null;
        }
        try {
            PreparedStatement pstmt = connection.prepareStatement(selectString);
            if (where != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs);
            }
            pstmt.execute();
            ResultSet resultSet = pstmt.getResultSet();
            boolean hasResult = resultSet.next();
            if (hasResult && resultSet.getRow() > 0) {
                while (!resultSet.isAfterLast()) {
                    SQLTableObject sqlTable = sqlTableObject.getClass().newInstance();
                    List<Pair<?>> attributes = sqlTable.getAllAttributes();
                    for (Pair<?> pair : attributes) {
                        try {
                            Object res = resultSet.getObject(pair.k());
                            pair.setValueUnsecure(res);
                        } catch (Exception e) {
                            if (!e.getClass().equals(SQLException.class)) {
                                out("load().exception." + e.getClass().toString() + " " + e.getMessage());
                                System.err.println("SQLQueries.load.Exception on setting Pair: " + pair.k());
                            }
                        }
                    }
                    result.add((T) sqlTable);
                    resultSet.next();
                }
            }
            resultSet.close();
            pstmt.close();
            return result;
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
    }

    @Override
    public <T extends SQLTableObject> List<T> loadString(List<Pair<?>> columns, T sqlTableObject,
                                                         String selectString, List<Object> arguments) throws SqlQueriesException {
        lockRead();
        ArrayList<T> result = new ArrayList<>();
        out("loadString()");
        out(selectString);
        try {
            PreparedStatement pstmt = connection.prepareStatement(selectString);
            if (arguments != null) {
                int count = 1;
                for (Object object : arguments) {
                    pstmt.setObject(count, object);
                    count++;
                }
            }
            pstmt.execute();
            ResultSet resultSet = pstmt.getResultSet();
            while (resultSet.next() && !resultSet.isAfterLast()) {
                T sqlObjInstance = (T) sqlTableObject.getClass().newInstance();
                List<Pair<?>> attributes = sqlObjInstance.getAllAttributes();
                for (Pair<?> pair : attributes) {
                    Object res = resultSet.getObject(pair.k());
                    pair.setValueUnsecure(res);
                }
                result.add(sqlObjInstance);
            }
            resultSet.close();
            pstmt.close();
            return result;
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        } finally {
            unlockRead();
        }
    }

    @Override
    public <T> T queryValue(String query, Class<T> clazz) throws SqlQueriesException {
        return queryValue(query, clazz, null);
    }

    /**
     * @param query
     * @return true if the first result is a ResultSet object; false if the
     * first result is an update count or there is no result
     */
    @Override
    public <T> T queryValue(String query, Class<T> clazz, List<Object> args) throws SqlQueriesException {
        lockRead();
        Object result = null;
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            if (args != null) {
                int count = 1;
                for (Object arg : args) {
                    pstmt.setObject(count, arg);
                }
            }
            result = pstmt.execute();
            if ((boolean) result) {
                ResultSet resultSet = pstmt.getResultSet();
                resultSet.next();
                if (resultSet.getRow() > 0) {
                    //String columnName = resultSet.getMetaData().getColumnLabel(1);
                    result = resultSet.getObject(1);
                    resultSet.close();
                    pstmt.close();
                }
            } else {
                pstmt.close();
                return null;
            }
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        } finally {
            unlockRead();
        }
        return resultTransformer.convert(clazz, result);
    }

    /**
     * see loadString... duplicate? nope
     *
     * @param statement
     * @param whereArgs
     * @return
     * @throws SqlQueriesException
     */
    @Override
    public void execute(String statement, List<Object> whereArgs) throws SqlQueriesException {
        lockRead();
        try {
            PreparedStatement pstmt = connection.prepareStatement(statement);
            if (whereArgs != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs, 1);
            }
            pstmt.execute();
            pstmt.close();
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        } finally {
            unlockRead();
        }
    }


    @Override
    public Long insert(SQLTableObject sqlTableObject) throws SqlQueriesException {
        return insertWithAttributes(sqlTableObject, sqlTableObject.getInsertAttributes());
    }

    @Override
    public Long insertWithAttributes(SQLTableObject sqlTableObject, List<Pair<?>> attributes) throws SqlQueriesException {
        lockWrite();
        Boolean next;
        out("insert()");
        String query = null;
        String fromTable = sqlTableObject.getTableName();
        if (attributes == null) {
            System.err.println("SQLQueries.insertWithAttributes: attributes are null.");
            System.err.println("SQLQueries.insertWithAttributes: have you called init() in the constructor of " + sqlTableObject.getClass().getSimpleName() + "?");
        }
        try {
            query = " insert into " + fromTable + " (";
            String toConcat = ") values (";
            for (int i = 0; i < attributes.size(); i++) {
                String key = attributes.get(i).k();
                if (i < attributes.size() - 1) {
                    query += key + ", ";
                    toConcat += " ? , ";
                } else {
                    query += key;
                    toConcat += " ? ";
                }
            }
            query += toConcat + ")";
            out("insert.query: " + query);
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
        try {

            PreparedStatement pstmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            for (int i = 1; i <= attributes.size(); i++) {
                Pair<?> attribute = attributes.get(i - 1);
                pstmt.setObject(i, attribute.v());
            }
            if (reentrantWriteLock != null)
                reentrantWriteLock.lock();
            pstmt.executeUpdate();
            ResultSet resultSet = pstmt.getGeneratedKeys();
            next = resultSet.next();
            if (resultSet.getRow() > 0) {
                Object id = resultSet.getObject(1);
                resultSet.close();
                pstmt.close();
                if (reentrantWriteLock != null)
                    reentrantWriteLock.unlock();
                if (id instanceof Integer)
                    return Long.valueOf((Integer) id);
                if (id instanceof Long)
                    return (Long) id;
            }
        } catch (Exception e) {
            //e.printStackTrace();
            if (reentrantWriteLock != null)
                reentrantWriteLock.unlock();
            System.err.println("SQLQueries.insert.query: " + query);
            System.err.println("SQLQueries.insert.attributes: ");
            for (Pair pair : attributes) {
                System.err.print(pair.v() + ", ");
            }
            throw new SqlQueriesException(e);
        } finally {
            unlockWrite();
        }
        out("insert().doing nothing right now");
        return null;
    }


    @Override
    public void lockRead() {
//        if (lock != null)
//            lock.lockRead();
    }

    @Override
    public void unlockRead() {
//        if (lock != null)
//            lock.unlockRead();
    }

    @Override
    public void lockWrite() {
//        if (lock != null)
//            lock.lockWrite();
    }

    @Override
    public void unlockWrite() {
//        if (lock != null)
//            lock.unlockWrite();
    }


    public void beginTransaction() throws SQLException {
        connection.setAutoCommit(false);
    }


    public void commit() throws SQLException {
        connection.commit();
    }


    public void rollback() throws SQLException {
        connection.rollback();
        connection.setAutoCommit(true);
    }

    /**
     * Queries a single value, not a row
     *
     * @param query
     * @param arguments
     * @param resultClass
     * @param <C>
     * @return
     * @throws SqlQueriesException
     */
    @Override
    public <C> C querySingle(String query, List<Object> arguments, Class<C> resultClass) throws SqlQueriesException {
        lockRead();
        try {
            out("SQLQueries.query");
            PreparedStatement pstmt = connection.prepareStatement(query);
            if (arguments != null) {
                int count = 1;
                for (Object object : arguments) {
                    pstmt.setObject(count, object);
                    count++;
                }
            }
            pstmt.execute();
            ResultSet resultSet = pstmt.getResultSet();
            resultSet.next();
            if (resultSet.getRow() > 0)
                while (!resultSet.isAfterLast()) {
                    return resultTransformer.convert(resultClass, resultSet.getObject(1));
                }
            resultSet.close();
            pstmt.close();
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        } finally {
            unlockRead();
        }
        return null;
    }

    @Override
    public <T extends SQLTableObject> T loadFirstRow(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> whereArgs, Class<T> castClass) throws SqlQueriesException {
        List<T> list = load(columns, sqlTableObject, where, whereArgs, "limit 1");
        if (list.size() > 0)
            return list.get(0);
        return null;
    }


    @Override
    public void onShutDown() {
        try {
            if (connection != null)
                connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

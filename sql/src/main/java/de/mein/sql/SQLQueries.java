package de.mein.sql;


import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * builds and executes mysql queries for several purposes.
 *
 * @author deck006
 */
@SuppressWarnings("Duplicates")
public class SQLQueries {

    private static Logger logger = Logger.getLogger(SQLQueries.class);
    private RWLock lock;
    private static final boolean sysout = false;
    private final Connection connection;

    private static void out(String msg) {
        if (sysout) {
            logger.debug("SqlQueries.out()." + msg);
        }
    }

    public SQLQueries(Connection connection) {
        this.connection = connection;
    }

    public SQLQueries(Connection connection, RWLock lock) {
        this.connection = connection;
        this.lock = lock;
    }

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
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            System.err.println(e.getSQLState());
            logger.error("query failed: " + query);
            logger.errorWhereArgs(whereArgs);
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        } finally {
            unlockWrite();
        }
    }

    public void delete(SQLTableObject sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        lockWrite();
        String query = "delete from " + sqlTableObject.getTableName() + " where " + where;
        out("delete().query= " + query);
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            if (where != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs);
            }
            int result = pstmt.executeUpdate();
            pstmt.close();
        } catch (Exception e) {
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        } finally {
            unlockWrite();
        }
    }

    private String buildInsertModifyQuery(List<Pair<?>> what, String before, String after, String where,
                                          String fromTable) throws SqlQueriesException {
        String query;
        try {
            query = before + " " + fromTable + " " + after + " ";
            for (int i = 0; i < what.size(); i++) {
                String key = what.get(i).k();
                if (i < what.size() - 1) {
                    query += key + "= ? , ";
                } else {
                    query += key + " = ?";
                }
            }
            if (where != null) {
                query += " where " + where;
            }

        } catch (Exception e) {
            logger.error("sqlQueries.buildInsertModifyQuery()");
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        }
        return query;
    }

    private void insertArguments(PreparedStatement pstmt, List<Object> whereArgs, int count) throws SQLException {
        for (Object o : whereArgs) {
            pstmt.setObject(count, o);
            count++;
        }
    }

    private void insertArguments(PreparedStatement pstmt, List<Object> whereArgs) throws SQLException {
        insertArguments(pstmt, whereArgs, 1);
    }

    public <T extends SQLTableObject> SQLResource<T> loadResource(List<Pair<?>> columns, Class<T> clazz, String where,
                                                                  List<Object> whereArgs) throws SqlQueriesException, IllegalAccessException, InstantiationException {
        String fromTable = clazz.newInstance().getTableName();
        String selectString = buildSelectQuery(columns, fromTable);
        if (where != null) {
            selectString += " where " + where;
        }
        if (connection == null) {
            return null;
        }
        try {
            PreparedStatement pstmt = connection.prepareStatement(selectString);
            if (where != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs);
            }
            pstmt.execute();
            return new SQLResource<>(pstmt, clazz);
        } catch (Exception e) {
            logger.error("sqlQueries.load()");
            logger.error(selectString);
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        }
    }


    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        return load(columns, sqlTableObject, where, whereArgs, null);
    }

    public <T> List<T> loadColumn(Pair<T> column, Class<T> clazz, SQLTableObject sqlTableObject, String where, List<Object> whereArgs, String whatElse) throws SqlQueriesException {
        List<T> result = new ArrayList<>();
        out("load()");
        String fromTable = sqlTableObject.getTableName();
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
            logger.error("sqlQueries.load()");
            logger.error(selectString);
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        }
    }

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
                                System.err.println("SQLQueries.load.Exception on setting Pair: "+pair.k());
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
            logger.error("sqlQueries.load()");
            logger.error(selectString);
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        }
    }

    public List<SQLTableObject> loadString(List<Pair<?>> columns, SQLTableObject sqlTableObject,
                                           String selectString, List<Object> arguments) throws SqlQueriesException {
        lockRead();
        ArrayList<SQLTableObject> result = new ArrayList<>();
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
                SQLTableObject sqlObjInstance = sqlTableObject.getClass().newInstance();
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
            logger.error("sqlQueries.loadString()");
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        } finally {
            unlockRead();
        }
    }

    /**
     * @param query
     * @return true if the first result is a ResultSet object; false if the
     * first result is an update count or there is no result
     */
    public <T> T queryValue(String query, Class<T> clazz) throws SqlQueriesException {
        lockRead();
        Object result = null;
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            result = pstmt.execute();
            if ((boolean) result) {
                ResultSet resultSet = pstmt.getResultSet();
                resultSet.next();
                if (resultSet.getRow() > 0) {
                    String columnName = resultSet.getMetaData().getColumnLabel(1);
                    result = resultSet.getObject(columnName);
                    resultSet.close();
                    pstmt.close();
                }
            } else {
                pstmt.close();
                return null;
            }
        } catch (Exception e) {
            logger.error("sqlQueries.query()");
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        } finally {
            unlockRead();
        }
        return (T) result;
    }

    /**
     * see loadString... duplicate?
     *
     * @param query
     * @param whereArgs
     * @return
     * @throws SqlQueriesException
     */
    public List<SQLTableObject> query(String query, List<Object> whereArgs) throws SqlQueriesException {
        lockRead();
        Object result = null;
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            if (whereArgs != null && whereArgs != null) {
                insertArguments(pstmt, whereArgs, 1);
            }
            result = pstmt.execute();
            if ((boolean) result) {
                ResultSet resultSet = pstmt.getResultSet();
                resultSet.next();
                if (resultSet.getRow() > 0) {
                    String columnName = resultSet.getMetaData().getColumnLabel(1);
                    result = resultSet.getObject(columnName);
                    resultSet.close();
                    pstmt.close();
                }
            } else {
                pstmt.close();
                return null;
            }
        } catch (Exception e) {
            logger.error("sqlQueries.query()");
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        } finally {
            unlockRead();
        }
        return (List<SQLTableObject>) result;
    }

    public void backup() throws SQLException {
        Statement stmt = this.connection.createStatement();
        stmt.executeUpdate("backup to backup.db");
    }

    public Long insert(SQLTableObject sqlTableObject) throws SqlQueriesException {
        return insertWithAttributes(sqlTableObject, sqlTableObject.getInsertAttributes());
    }

    public Long insertWithAttributes(SQLTableObject sqlTableObject, List<Pair<?>> attributes) throws SqlQueriesException {
        lockWrite();
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
            logger.error("sqlQueries.insert()");
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        }

        try {
            PreparedStatement pstmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            for (int i = 1; i <= attributes.size(); i++) {
                Pair<?> attribute = attributes.get(i - 1);
                pstmt.setObject(i, attribute.v());
            }
            pstmt.executeUpdate();
            ResultSet resultSet = pstmt.getGeneratedKeys();
            resultSet.next();
            if (resultSet.getRow() > 0) {
                Object id = resultSet.getObject(1);
                if (id instanceof Integer)
                    return Long.valueOf((Integer) id);
                if (id instanceof Long)
                    return (Long) id;
            }
            //n2
            resultSet.close();
            pstmt.close();
        } catch (Exception e) {
            //e.printStackTrace();
            throw new SqlQueriesException(e);
        } finally {
            unlockWrite();
        }
        out("insert().doing nothing right now");
        return null;
    }

    private String buildSelectQuery(List<Pair<?>> what, String fromTable) {
        String result = "select ";
        for (int i = 0; i < what.size(); i++) {
            String entry = what.get(i).k();
            if (i < what.size() - 1) {
                result += entry + ", ";
            } else {
                result += entry + " ";
            }
        }
        result += " from " + fromTable;
        return result;
    }

    public void lockRead() {
//        if (lock != null)
//            lock.lockRead();
    }

    public void unlockRead() {
//        if (lock != null)
//            lock.unlockRead();
    }

    public void lockWrite() {
//        if (lock != null)
//            lock.lockWrite();
    }

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
                    return (C) resultSet.getObject(1);
                }
            resultSet.close();
            pstmt.close();
        } catch (Exception e) {
            logger.error("sqlQueries.query()");
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        } finally {
            unlockRead();
        }
        return null;
    }

    public static List<Object> whereArgs(Object... values) {
        List<Object> args = new ArrayList<>();
        for (Object v : values) {
            args.add(v);
        }
        return args;
    }

    public <T> List<T> load(List<Pair<?>> columns, SQLTableObject sqlTableObject, String where, List<Object> whereArgs, String whatElse, Class<T> castClass) throws SqlQueriesException {
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
            logger.error("sqlQueries.load()");
            logger.error(selectString);
            logger.error("stacktrace", e);
            throw new SqlQueriesException(e);
        }
    }
}

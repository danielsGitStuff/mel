package de.mel.sql;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import de.mel.sql.conn.SQLConnection;

/**
 * Created by xor on 2/6/17.
 */
public abstract class ISQLQueries {
    public static final boolean SYSOUT = false;
    protected RWLock lock;
    protected ReentrantLock reentrantWriteLock;

    public static String buildPartIn(Iterable<?> set) {
        StringBuilder stringBuilder = new StringBuilder("(");
        Iterator<?> iterator = set.iterator();
        while (iterator.hasNext()) {
            Object o = iterator.next();
            stringBuilder.append("?");
            if (iterator.hasNext())
                stringBuilder.append(",");
        }
        stringBuilder.append(")");
        return stringBuilder.toString();
    }

    public static <T extends SQLTableObject> String buildQueryFrom(List<Pair<?>> columns, Class<T> clazz, String where) throws SqlQueriesException {
        try {
            String fromTable = clazz.newInstance().getTableName();
            String selectString = buildSelectQuery(columns, fromTable);
            if (where != null) {
                selectString += " where " + where;
            }
            return selectString;
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
    }

    public static List<Object> args(Object... values) {
        List<Object> args = new ArrayList<>();
        if (values != null)
            for (Object v : values) {
                args.add(v);
            }
        return args;
    }

    public static List<Pair<?>> columns(Pair... pairs) {
        List<Pair<?>> cols = new ArrayList<>();
        if (pairs != null) {
            for (Pair pair : pairs)
                cols.add(pair);
        }
        return cols;
    }

    public static String buildSelectQuery(List<Pair<?>> what, String fromTable) {
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

    protected void out(String msg) {
        if (SYSOUT) {
            System.out.println("SqlQueries.out()." + msg);
        }
    }

    protected String buildInsertModifyQuery(List<Pair<?>> what, String where,
                                            String fromTable) throws SqlQueriesException {
        StringBuilder query;
        try {
            query = new StringBuilder("update" + " " + fromTable + " " + "set" + " ");
            for (int i = 0; i < what.size(); i++) {
                String key = what.get(i).k();
                if (i < what.size() - 1) {
                    query.append(key).append("= ? , ");
                } else {
                    query.append(key).append(" = ?");
                }
            }
            if (where != null) {
                query.append(" where ").append(where);
            }

        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
        return query.toString();
    }

    public abstract SQLConnection getSQLConnection();

    public abstract void update(SQLTableObject sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException;

    public abstract void delete(SQLTableObject sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException;

    /**
     * loads a resource with where clause. if you need more fancy stuff look at loadQueryResource().
     *
     * @param columns
     * @param clazz
     * @param where
     * @param whereArgs
     * @param <T>
     * @return
     * @throws SqlQueriesException
     */
    public abstract <T extends SQLTableObject> ISQLResource<T> loadResource(List<Pair<?>> columns, Class<T> clazz, String where,
                                                                            List<Object> whereArgs) throws SqlQueriesException;


    public abstract <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> arguments) throws SqlQueriesException;

    public abstract <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> arguments, String whatElse) throws SqlQueriesException;

    /**
     * returns the first row only.
     * ADDS " LIMIT 1" TO THE QUERY!
     * @param columns
     * @param sqlTableObject
     * @param where
     * @param whereArgs
     * @param castClass
     * @param <T>
     * @return
     * @throws SqlQueriesException
     */
    public abstract <T extends SQLTableObject> T loadFirstRow(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> whereArgs, Class<T> castClass) throws SqlQueriesException;

//    public abstract <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, SQLTableObject sqlTableObject, String where, List<Object> whereArgs, String whatElse, Class<T> castClass) throws SqlQueriesException;

    public abstract <T> List<T> loadColumn(Pair<T> column, Class<T> clazz, SQLTableObject sqlTableObject, String tableReference, String where, List<Object> whereArgs, String whatElse) throws SqlQueriesException;

    public abstract <T> List<T> loadColumn(Pair<T> column, Class<T> clazz, String query, List<Object> whereArgs) throws SqlQueriesException;


    public abstract <T extends SQLTableObject> List<T> loadString(List<Pair<?>> columns, T sqlTableObject,
                                                                  String selectString, List<Object> arguments) throws SqlQueriesException;

    /**
     * @param query
     * @param clazz
     * @param <T>
     * @return a single value
     * @throws SqlQueriesException
     */
    public abstract <T> T queryValue(String query, Class<T> clazz) throws SqlQueriesException;

    /**
     * @param query
     * @param clazz
     * @param args
     * @param <T>
     * @return a single value
     * @throws SqlQueriesException
     */
    public abstract <T> T queryValue(String query, Class<T> clazz, List<Object> args) throws SqlQueriesException;

    public abstract void execute(String statement, List<Object> args) throws SqlQueriesException;

    public void execute(String statement) throws SqlQueriesException {
        execute(statement, null);
    }


    public abstract Long insert(SQLTableObject sqlTableObject) throws SqlQueriesException;

    public abstract Long insertWithAttributes(SQLTableObject sqlTableObject, List<Pair<?>> attributes) throws SqlQueriesException;

    public abstract void lockRead();

    public abstract void unlockRead();

    public abstract void lockWrite();

    public abstract void unlockWrite();


    public abstract void beginTransaction() throws SQLException;

    public abstract void rollback() throws SqlQueriesException;

    public abstract void commit() throws SQLException;

    public abstract void onShutDown();

    public abstract void close() throws SqlQueriesException;

    /**
     * lets you load a custom query. no separation of where clause and stuff
     *
     * @param query
     * @param allAttributes
     * @param clazz
     * @param args
     * @param <T>
     * @return
     * @throws SqlQueriesException
     */
    public abstract <T extends SQLTableObject> ISQLResource<T> loadQueryResource(String query, List<Pair<?>> allAttributes, Class<T> clazz, List<Object> args) throws SqlQueriesException;

    public void enableWAL() throws SqlQueriesException {

    }
}

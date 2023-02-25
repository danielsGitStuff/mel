package de.mel.android.sql;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;

import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import de.mel.Lok;
import de.mel.sql.ISQLQueries;
import de.mel.sql.ISQLResource;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;
import de.mel.sql.SqlQueriesException;
import de.mel.sql.conn.SQLConnection;

/**
 * does not implement {@link AutoCloseable} cause of compatibility with older android versions
 * Created by xor on 2/6/17.
 */
public class AndroidSQLQueries extends ISQLQueries {

    private final SQLiteDatabase db;
    private final AndroidDBConnection androidDBConnection;

    private static final Map<Class<?>, BiFunction<Cursor, Integer, Object>> classRedFunctionMap = new HashMap<>();

    static {
        classRedFunctionMap.put(boolean.class, (c, i) -> c.getInt(i) == 1);
        classRedFunctionMap.put(Boolean.class, (c, i) -> c.getInt(i) == 1);
        classRedFunctionMap.put(byte[].class, Cursor::getBlob);
        classRedFunctionMap.put(Byte[].class, Cursor::getBlob);
        classRedFunctionMap.put(Date.class, (c, i) -> {
            Long value = c.getLong(i);
            return new Date(TimeUnit.MILLISECONDS.toMillis(value));
        });
        classRedFunctionMap.put(double.class, Cursor::getDouble);
        classRedFunctionMap.put(Double.class, Cursor::getDouble);
        classRedFunctionMap.put(float.class, Cursor::getFloat);
        classRedFunctionMap.put(Float.class, Cursor::getFloat);
        classRedFunctionMap.put(int.class, Cursor::getInt);
        classRedFunctionMap.put(Integer.class, Cursor::getInt);
        classRedFunctionMap.put(long.class, Cursor::getLong);
        classRedFunctionMap.put(Long.class, Cursor::getLong);
        classRedFunctionMap.put(short.class, Cursor::getShort);
        classRedFunctionMap.put(Short.class, Cursor::getShort);
        classRedFunctionMap.put(String.class, Cursor::getString);
    }

    public static void readCursorIndexToPair(Cursor cursor, int index, Pair<?> pair) {
        if (index > -1) {
            if (cursor.isNull(index))
                pair.setValueUnsecure(null);
            else if (pair.getGenericClass().isEnum()) {
                String value = cursor.getString(index);
                Class<Enum> eType = (Class<Enum>) pair.getGenericClass();
                Enum en = Enum.valueOf(eType, value);
                pair.setValueUnsecure(en);
            } else {
                pair.setValueUnsecure(AndroidSQLQueries.classRedFunctionMap.get(pair.getGenericClass()).apply(cursor, index));
            }
        }
    }


    public AndroidSQLQueries(AndroidDBConnection androidDBConnection) {
        this.db = androidDBConnection.getDb();
        this.androidDBConnection = androidDBConnection;
    }

    @Override
    public SQLConnection getSQLConnection() {
        return androidDBConnection;
    }

    @Override
    public void update(SQLTableObject sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        db.update(sqlTableObject.getTableName(), createContentValues(sqlTableObject.getInsertAttributes()), where, argsToStringArgs(whereArgs));
    }

    @Override
    public void delete(SQLTableObject sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        db.delete(sqlTableObject.getTableName(), where, argsToStringArgs(whereArgs));
    }

    @Override
    public <T extends SQLTableObject> ISQLResource<T> loadResource(List<Pair<?>> columns, Class<T> clazz, String where, List<Object> whereArgs) throws SqlQueriesException {
        String query = ISQLQueries.buildQueryFrom(columns, clazz, where);
        SQLiteCursor cursor = (SQLiteCursor) db.rawQuery(query, this.argsToStringArgs(whereArgs));
        AndroidSQLResource<T> resource = new AndroidSQLResource<>(cursor, clazz);
        return resource;
    }

    private String[] pairListToArr(List<Pair<?>> pairs) {
        String[] cols = new String[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            Pair pair = pairs.get(i);
            cols[i] = pair.k();
        }
        return cols;
    }


    @Override
    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> arguments) throws SqlQueriesException {
        return load(columns, sqlTableObject, where, arguments, null);
    }


    @Override
    public <T> List<T> loadColumn(Pair<T> column, Class<T> clazz, SQLTableObject sqlTableObject, String tableReference, String where, List<Object> whereArgs, String whatElse) throws SqlQueriesException {
        List<Pair<?>> columns = new ArrayList<>();
        columns.add(column);
        String selectString = buildSelectQuery(columns, sqlTableObject.getTableName());
        if (tableReference != null)
            selectString += " " + tableReference;
        selectString += " where " + where;
        List<T> result;
        Cursor cursor = db.rawQuery(selectString, argsToStringArgs(whereArgs));
        try {
            result = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                AndroidSQLQueries.readCursorToPair(cursor, column);
                result.add(column.v());
            }
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return result;
    }

    @Override
    public <T> List<T> loadColumn(Pair<T> column, Class<T> clazz, String query, List<Object> whereArgs) throws SqlQueriesException {
        List<T> result;
        Cursor cursor = db.rawQuery(query, argsToStringArgs(whereArgs));
        try {
            result = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                AndroidSQLQueries.readCursorIndexToPair(cursor, 0, column);
                result.add(column.v());
            }
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return result;
    }

    @Override
    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> arguments, String whatElse) throws SqlQueriesException {
        String select = buildSelectQuery(columns, sqlTableObject.getTableName());
        if (where != null) {
            select += " where " + where;
        }
        if (whatElse != null) {
            select += " " + whatElse;
        }
        return loadString(columns, sqlTableObject, select, arguments);
    }

    @Override
    public <T extends SQLTableObject> List<T> loadString(List<Pair<?>> columns, T sqlTableObject, String selectString, List<Object> arguments) throws SqlQueriesException {
        List<T> result;
        Cursor cursor = db.rawQuery(selectString, argsToStringArgs(arguments));
        try {
            result = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                T ins = (T) sqlTableObject.getClass().newInstance();
                for (Pair<?> pair : ins.getAllAttributes()) {
                    AndroidSQLQueries.readCursorToPair(cursor, pair);
                }
                result.add(ins);
            }
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return result;
    }

    public static void readCursorToPair(Cursor cursor, Pair<?> pair) {
        int index = cursor.getColumnIndex(pair.k());
        readCursorIndexToPair(cursor, index, pair);
    }


    @Override
    public <T> T queryValue(String query, Class<T> clazz) throws SqlQueriesException {
        return queryValue(query, clazz, null);
    }

    @Override
    public <T> T queryValue(String query, Class<T> clazz, List<Object> args) throws SqlQueriesException {
        T value;
        Cursor cursor = db.rawQuery(query, argsToStringArgs(args));
        try {
            value = readValue(cursor, clazz);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return value;
    }

    private <T> T readValue(Cursor cursor, Class<T> clazz) {
        if (cursor.moveToNext() && cursor.getCount() == 1 && cursor.getColumnCount() == 1) {
            int index = 0;
            Object res = null;
            if (clazz.equals(Double.class) || clazz.equals(double.class))
                res = cursor.getDouble(index);
            else if (clazz.equals(Float.class) || clazz.equals(float.class))
                res = cursor.getFloat(index);
            else if (clazz.equals(Integer.class) || clazz.equals(int.class))
                res = cursor.getInt(index);
            else if (clazz.equals(Short.class) || clazz.equals(short.class))
                res = cursor.getShort(index);
            else if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
                Integer v = cursor.getInt(index);
                res = (v == index);
            } else if (clazz.equals(Long.class) || clazz.equals(long.class))
                res = cursor.getLong(index);
            else if (clazz.equals(byte[].class))
                res = cursor.getBlob(index);
            else if (clazz.equals(Byte[].class))
                res = cursor.getBlob(index);
            else if (clazz.equals(String.class))
                res = cursor.getString(index);
            else {
                System.err.println("AndroidSQLQueries.UNKOWN TYPE");
            }
            return (T) res;
        }
        return null;
    }


    @Override
    public void execute(String query, List<Object> args) throws SqlQueriesException {
        db.execSQL(query, argsToAndroidValues(args));
    }


    @Override
    public Long insert(SQLTableObject sqlTableObject) throws SqlQueriesException {
        return insertWithAttributes(sqlTableObject, sqlTableObject.getInsertAttributes());
    }

    @Override
    public Long insertWithAttributes(SQLTableObject sqlTableObject, List<Pair<?>> attributes) throws SqlQueriesException {
        lockWrite();
        ContentValues contentValues = createContentValues(attributes);
        try {
            Long id = db.insertOrThrow(sqlTableObject.getTableName(), null, contentValues);
            unlockWrite();
            return id;
        } catch (Exception e) {
            System.err.println(getClass().getSimpleName() + ".insertWithAttributes().exception: " + e.getClass().getSimpleName() + " ... " + e.getMessage());
//            System.err.println("e: " + sqlTableObject.toString());
            throw new SqlQueriesException(e);
        }
    }

    @Override
    public void lockRead() {

    }

    @Override
    public void unlockRead() {

    }

    @Override
    public void lockWrite() {

    }

    @Override
    public void unlockWrite() {

    }

    @Override
    public void beginTransaction() throws SQLException {

    }

    @Override
    public void rollback() throws SqlQueriesException {

    }

    @Override
    public void commit() throws SQLException {

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

    }

    @Override
    public void close() throws SqlQueriesException {
        db.close();
    }

    @Override
    public <T extends SQLTableObject> ISQLResource<T> loadQueryResource(String query, List<Pair<?>> allAttributes, Class<T> clazz, List<Object> args) {
        SQLiteCursor cursor = (SQLiteCursor) db.rawQuery(query, this.argsToStringArgs(args));
        AndroidSQLResource<T> resource = new AndroidSQLResource<>(cursor, clazz);
        return resource;
    }

    private static String[] argsToStringArgs(List<Object> whereArgs) {
        if (whereArgs == null || whereArgs.size() == 0)
            return null;
        int pos = 0;
        String[] result = new String[whereArgs.size()];
        for (Object o : whereArgs) {
            if (o == null) {
                result[pos] = "null";
            } else {
                Class c = o.getClass();
                if (c.equals(Boolean.class))
                    result[pos] = ((Boolean) o) ? "1" : "0";
                else
                    result[pos] = o.toString();
            }
            pos++;
        }
        return result;
    }

    /**
     * See: <br>
     * https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#execSQL%28java.lang.String%29<br>
     * "bindArgs : Object: only byte[], String, Long and Double are supported in bindArgs."
     *
     * @param args
     * @return
     */
    private Object[] argsToAndroidValues(List<Object> args) {
        if (args != null) {
            int pos = 0;
            Object[] values = new Object[args.size()];
            for (Object a : args) {
                if (a != null) {
                    //type conversion
                    Class c = a.getClass();
                    if (c.equals(Long.class) || c.equals(long.class)
                            || c.equals(Double.class) || c.equals(double.class)
                            || c.equals(Byte[].class) || c.equals(byte[].class)
                            || c.equals(String.class))
                        values[pos] = a;
                    else if (a.getClass().equals(Boolean.class))
                        values[pos] = ((Boolean) a) ? 1L : 0L;
                    else if (a.getClass().equals(Float.class))
                        values[pos] = (Double) a;
                    else if (a.getClass().equals(Integer.class))
                        values[pos] = (Long) a;
                    else if (a.getClass().equals(Integer.class))
                        values[pos] = (Short) a;
                    else if (a.getClass().isEnum()) {
                        values[pos] = (String) ((Enum) a).name();
                    } else {
                        Lok.error("TYPE:CONVERSION:FAILED:FOR: " + a.getClass().getSimpleName());
                    }
                }
                pos++;
            }
            return values;
        } else
            return new Object[0];
    }

    private ContentValues createContentValues(List<Pair<?>> pairs) {
        ContentValues contentValues = new ContentValues();
        for (Pair<?> pair : pairs) {
            try {
                if (pair.getGenericClass().equals(Double.class))
                    contentValues.put(pair.k(), (Double) pair.v());
                else if (pair.getGenericClass().equals(Float.class))
                    contentValues.put(pair.k(), (Float) pair.v());
                else if (pair.getGenericClass().equals(Integer.class))
                    contentValues.put(pair.k(), (Integer) pair.v());
                else if (pair.getGenericClass().equals(Short.class))
                    contentValues.put(pair.k(), (Short) pair.v());
                else if (pair.getGenericClass().equals(Boolean.class))
                    contentValues.put(pair.k(), (Boolean) pair.v());
                else if (pair.getGenericClass().equals(Long.class))
                    contentValues.put(pair.k(), (Long) pair.v());
                else if (pair.getGenericClass().equals(byte[].class))
                    contentValues.put(pair.k(), (byte[]) pair.v());
                else if (pair.getGenericClass().equals(String.class))
                    contentValues.put(pair.k(), (String) pair.v());
                else if (pair.getGenericClass().isEnum())
                    contentValues.put(pair.k(), (String) ((Enum) pair.v()).name());
                else if (pair.getGenericClass().equals(Date.class)) {
                    Long value = pair.isNull() ? null : ((Date) pair.v()).getTime();
                    contentValues.put(pair.k(), value);
                } else {
                    System.err.println("AndroidSQLQueries.createContentValues.UNKOWN TYPE");
                }
            } catch (ClassCastException e) {
                e.printStackTrace();
            }
        }
        return contentValues;
    }

}

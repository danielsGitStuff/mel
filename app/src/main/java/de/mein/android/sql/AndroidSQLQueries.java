package de.mein.android.sql;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.mein.android.drive.service.AndroidDBConnection;
import de.mein.sql.ISQLQueries;
import de.mein.sql.ISQLResource;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;
import de.mein.sql.con.SQLConnection;

/**
 * Created by xor on 2/6/17.
 */

public class AndroidSQLQueries extends ISQLQueries {

    private final SQLiteDatabase db;
    private final AndroidDBConnection androidDBConnection;


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
        Cursor cursor = db.rawQuery(query, this.argsToStringArgs(whereArgs));
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
    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        return load(columns, sqlTableObject, where, whereArgs, null);
    }

    @Override
    public <T> List<T> loadColumn(Pair<T> column, Class<T> clazz, SQLTableObject sqlTableObject, String where, List<Object> whereArgs, String whatElse) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.loadColumn!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.loadColumn!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.loadColumn!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.loadColumn!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.loadColumn!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.loadColumn!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.loadColumn!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.loadColumn!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        return null;
    }

    @Override
    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> args, String whatElse) throws SqlQueriesException {
        String select = buildSelectQuery(columns, sqlTableObject.getTableName());
        if (where != null) {
            select += " where " + where;
        }
        if (whatElse != null) {
            select += " " + whatElse;
        }
        return loadString(columns, sqlTableObject, select, args);
    }

    @Override
    public <T extends SQLTableObject> List<T> loadString(List<Pair<?>> columns, T sqlTableObject, String selectString, List<Object> arguments) throws SqlQueriesException {
        System.out.println("AndroidSQLQueries.loadString");
        Cursor cursor = db.rawQuery(selectString, argsToStringArgs(arguments));
        List<T> result = new ArrayList<>(cursor.getCount());
        try {
            while (cursor.moveToNext()) {
                T ins = (T) sqlTableObject.getClass().newInstance();
                for (Pair<?> pair : ins.getAllAttributes()) {
                    AndroidSQLQueries.readCursorToPair(cursor, pair);
                }
                result.add(ins);
            }
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
        return result;
    }

    public static void readCursorToPair(Cursor cursor, Pair<?> pair) {
        int index = cursor.getColumnIndex(pair.k());
        if (index > -1) {
            if (cursor.isNull(index))
                pair.setValueUnsecure(null);
            else if (pair.getGenericClass().equals(Double.class))
                pair.setValueUnsecure(cursor.getDouble(index));
            else if (pair.getGenericClass().equals(Float.class))
                pair.setValueUnsecure(cursor.getFloat(index));
            else if (pair.getGenericClass().equals(Integer.class))
                pair.setValueUnsecure(cursor.getInt(index));
            else if (pair.getGenericClass().equals(Short.class))
                pair.setValueUnsecure(cursor.getShort(index));
            else if (pair.getGenericClass().equals(Boolean.class)) {
                Integer v = cursor.getInt(index);
                pair.setValueUnsecure(v == 1);
            } else if (pair.getGenericClass().equals(Long.class))
                pair.setValueUnsecure(cursor.getLong(index));
            else if (pair.getGenericClass().equals(byte[].class))
                pair.setValueUnsecure(cursor.getBlob(index));
            else if (pair.getGenericClass().equals(Byte[].class))
                pair.setValueUnsecure(cursor.getBlob(index));
            else if (pair.getGenericClass().equals(String.class))
                pair.setValueUnsecure(cursor.getString(index));
            else {
                System.err.println("AndroidSQLQueries.readCursorToPair.UNKOWN TYPE");
            }
        }
    }

    @Override
    public <T> T queryValue(String query, Class<T> clazz) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.queryValue");
        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToNext()) {
            Object res = null;
            int index = 0;
            if (cursor.getCount() == 1 && cursor.getColumnCount() == 1) {
                if (clazz.equals(Double.class))
                    res = cursor.getDouble(index);
                else if (clazz.equals(Float.class))
                    res = cursor.getFloat(index);
                else if (clazz.equals(Integer.class))
                    res = cursor.getInt(index);
                else if (clazz.equals(Short.class))
                    res = cursor.getShort(index);
                else if (clazz.equals(Boolean.class)) {
                    Integer v = cursor.getInt(index);
                    res = (v == index);
                } else if (clazz.equals(Long.class))
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
        Long id = db.insert(sqlTableObject.getTableName(), null, contentValues);
        unlockWrite();
        return id;
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
    public void commit() throws SQLException {

    }

    @Override
    public <C> C querySingle(String query, List<Object> arguments, Class<C> resultClass) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        System.err.println("AndroidSQLQueries.querySingle!!!!!!!!!!!!!!!!!!!!!!!!");
        return null;
    }

    @Override
    public <T> List<T> load(List<Pair<?>> columns, SQLTableObject sqlTableObject, String where, List<Object> whereArgs, String whatElse, Class<T> castClass) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        System.err.println("AndroidSQLQueries.load!!!!!!!!!!!!!!!!!!a");
        return null;
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
                    else {
                        System.out.println("AndroidSQLQueries.argsToAndroidValues.TYPE:CONVERSION:FAILED:FOR: " + a.getClass().getSimpleName());
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
            else {
                System.err.println("AndroidSQLQueries.createContentValues.UNKOWN TYPE");
            }
        }
        return contentValues;
    }

}

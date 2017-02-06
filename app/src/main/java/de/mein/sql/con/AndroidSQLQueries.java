package de.mein.sql.con;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.mein.drive.service.AndroidDBConnection;
import de.mein.sql.ISQLQueries;
import de.mein.sql.ISQLResource;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

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
        System.err.println("AndroidSQLQueries.update");
    }

    @Override
    public void delete(SQLTableObject sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        db.delete(sqlTableObject.getTableName(),where,argsListToArr(whereArgs));
    }

    @Override
    public <T extends SQLTableObject> ISQLResource<T> loadResource(List<Pair<?>> columns, Class<T> clazz, String where, List<Object> whereArgs) throws SqlQueriesException, IllegalAccessException, InstantiationException {
        System.err.println("AndroidSQLQueries.loadResource");
        return null;
    }

    private String[] pairListToArr(List<Pair<?>> pairs) {
        String[] cols = new String[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            Pair pair = pairs.get(i);
            cols[i] = pair.k();
        }
        return cols;
    }

    private String[] argsListToArr(List<Object> args) {
        if (args == null)
            return new String[0];
        String[] cols = new String[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Object o = args.get(i);
            if (o != null)
                cols[i] = o.toString();
        }
        return cols;
    }


    @Override
    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> whereArgs) throws SqlQueriesException {
        return load(columns,sqlTableObject,where,whereArgs,null);
    }

    @Override
    public <T> List<T> loadColumn(Pair<T> column, Class<T> clazz, SQLTableObject sqlTableObject, String where, List<Object> whereArgs, String whatElse) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.loadColumn");
        return null;
    }

    @Override
    public <T extends SQLTableObject> List<T> load(List<Pair<?>> columns, T sqlTableObject, String where, List<Object> whereArgs, String whatElse) throws SqlQueriesException {
        String select = buildSelectQuery(columns,sqlTableObject.getTableName());
        if (where != null) {
            select += " where " + where;
        }
        if (whatElse != null) {
            select += " " + whatElse;
        }
        Cursor cursor = db.rawQuery(select,argsListToArr(whereArgs));
        List<T> result = new ArrayList<>(cursor.getCount());
        try {
            while (cursor.moveToNext()) {
                T ins = (T) sqlTableObject.getClass().newInstance();
                for (Pair<?> pair : ins.getAllAttributes()) {
                    int index = cursor.getColumnIndex(pair.k());
                    if (index > -1) {
                        if (pair.getGenericClass().equals(Double.class))
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
                            System.err.println("AndroidSQLQueries.UNKOWN TYPE");
                        }
                    }
                }
                result.add(ins);
            }
        } catch (Exception e) {
            throw new SqlQueriesException(e);
        }
        return result;
    }

    @Override
    public List<SQLTableObject> loadString(List<Pair<?>> columns, SQLTableObject sqlTableObject, String selectString, List<Object> arguments) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.loadString");
        return null;
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
    public List<SQLTableObject> execute(String query, List<Object> whereArgs) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.execute");
        return null;
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

    @Override
    public Long insert(SQLTableObject sqlTableObject) throws SqlQueriesException {
        ContentValues contentValues = createContentValues(sqlTableObject.getInsertAttributes());
        Long id = db.insert(sqlTableObject.getTableName(), null, contentValues);
        return id;
    }

    @Override
    public Long insertWithAttributes(SQLTableObject sqlTableObject, List<Pair<?>> attributes) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.insertWithAttributes");
        return null;
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
        System.err.println("AndroidSQLQueries.querySingle");
        return null;
    }

    @Override
    public <T> List<T> load(List<Pair<?>> columns, SQLTableObject sqlTableObject, String where, List<Object> whereArgs, String whatElse, Class<T> castClass) throws SqlQueriesException {
        System.err.println("AndroidSQLQueries.loadaaaaa");
        return null;
    }
}

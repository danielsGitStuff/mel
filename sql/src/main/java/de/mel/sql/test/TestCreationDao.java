package de.mel.sql.test;

import de.mel.sql.*;
import de.mel.sql.conn.SQLConnector;
import de.mel.sql.transform.SqlResultTransformer;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class TestCreationDao<T extends SQLTableObject> extends Dao {
    private File dbFile;
    private Map<String, T> nameMap = new HashMap<>();

    public TestCreationDao(File dbFile) throws SQLException, ClassNotFoundException {
        super(createSqlQueries(dbFile));
        this.dbFile = dbFile;
    }

    public TestCreationDao(TestCreationDao dao) throws SQLException, ClassNotFoundException {
        this(dao.dbFile);
    }

    public abstract String createName(T obj);


    public InsertFollower<T, TestCreationDao<T>> insert(T obj) throws SqlQueriesException {
        sqlQueries.insert(obj);
        nameMap.put(createName(obj), obj);
        return new InsertFollower<>(null, this, obj);
    }

    public void cleanUp() throws SqlQueriesException {
        sqlQueries.close();
        dbFile.delete();
    }

    public T get(String key) {
        return nameMap.get(key);
    }

    public static SQLQueries createSqlQueries(File dbFile) throws SQLException, ClassNotFoundException {
        return new SQLQueries(SQLConnector.createSqliteConnection(dbFile), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
    }

    public static class InsertFollower<T extends SQLTableObject, D extends TestCreationDao<T>> {
        private T inserted;
        private D dao;
        private InsertFollower<T, D> parent;

        public InsertFollower(InsertFollower<T, D> parent, D dao, T inserted) {
            this.inserted = inserted;
            this.dao = dao;
            this.parent = parent;
        }

        public InsertFollower<T, D> let(Consumer<T> consumer) {
            consumer.accept(inserted);
            return this;
        }

        public InsertFollower<T, D> up() {
            return parent;
        }

        public InsertFollower<T, D> insert(Function<T, T> function) throws SqlQueriesException {
            T created = function.apply(this.inserted);
            dao.insert(created);
            return new InsertFollower<T, D>(this, this.dao, created);
        }
    }
}

package de.mein.sql;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by xor on 25.10.2015.
 */
public abstract class Dao {
    protected ISQLQueries sqlQueries;
    protected final boolean lock;

    public Dao(ISQLQueries sqlQueries) {
        this(sqlQueries, true);
    }

    public Dao(ISQLQueries sqlQueries, boolean lock) {
        this.sqlQueries = sqlQueries;
        this.lock = lock;
    }

    public ISQLQueries getSqlQueries() {
        return sqlQueries;
    }

    /**
     * Created by xor on 11/25/16.
     */
    public static class LockingDao extends ConnectionLockingDao {


        public LockingDao(ISQLQueries ISQLQueries) {
            super(ISQLQueries);
        }

        public LockingDao(ISQLQueries ISQLQueries, boolean lock) {
            super(ISQLQueries, lock);
        }
    }

    /**
     * Created by xor on 11/25/16.
     */
    public abstract static class ConnectionLockingDao extends Dao {
        public ConnectionLockingDao(ISQLQueries ISQLQueries) {
            super(ISQLQueries);
        }

        public ConnectionLockingDao(ISQLQueries ISQLQueries, boolean lock) {
            super(ISQLQueries, lock);
        }

    }
}

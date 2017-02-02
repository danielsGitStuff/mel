package de.mein.sql;

/**
 * Created by xor on 25.10.2015.
 */
public abstract class Dao {
    protected SQLQueries sqlQueries;
    protected final boolean lock;

    public Dao(SQLQueries sqlQueries) {
        this(sqlQueries, true);
    }

    public Dao(SQLQueries sqlQueries, boolean lock) {
        this.sqlQueries = sqlQueries;
        this.lock = lock;
    }

    public SQLQueries getSqlQueries() {
        return sqlQueries;
    }


    /**
     * Created by xor on 11/25/16.
     */
    public static class LockingDao extends ConnectionLockingDao {

        protected RWLock lock = new RWLock();

        public LockingDao(SQLQueries sqlQueries) {
            super(sqlQueries);
        }

        public LockingDao(SQLQueries sqlQueries, boolean lock) {
            super(sqlQueries, lock);
        }

        @Override
        public void lockRead() {
            lock.lockRead();
        }

        @Override
        public void lockWrite() {
            lock.lockWrite();
        }

        @Override
        public void unlockRead() {
            lock.unlockRead();
        }

        @Override
        public void unlockWrite() {
            lock.unlockWrite();
        }
    }

    /**
     * Created by xor on 11/25/16.
     */
    public abstract static class ConnectionLockingDao extends Dao {
        public ConnectionLockingDao(SQLQueries sqlQueries) {
            super(sqlQueries);
        }

        public ConnectionLockingDao(SQLQueries sqlQueries, boolean lock) {
            super(sqlQueries, lock);
        }

        public void lockWrite() {
            sqlQueries.lockWrite();
        }

        public void lockRead() {
            sqlQueries.lockRead();
        }

        public void unlockWrite() {
            sqlQueries.unlockWrite();
        }

        public void unlockRead() {
            sqlQueries.unlockRead();
        }
    }
}

package de.mein.sql;

/**
 * Created by xor on 25.10.2015.
 */
public abstract class Dao {
    protected ISQLQueries ISQLQueries;
    protected final boolean lock;

    public Dao(ISQLQueries ISQLQueries) {
        this(ISQLQueries, true);
    }

    public Dao(ISQLQueries ISQLQueries, boolean lock) {
        this.ISQLQueries = ISQLQueries;
        this.lock = lock;
    }

    public ISQLQueries getSqlQueries() {
        return ISQLQueries;
    }


    /**
     * Created by xor on 11/25/16.
     */
    public static class LockingDao extends ConnectionLockingDao {

        protected RWLock lock = new RWLock();

        public LockingDao(ISQLQueries ISQLQueries) {
            super(ISQLQueries);
        }

        public LockingDao(ISQLQueries ISQLQueries, boolean lock) {
            super(ISQLQueries, lock);
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
        public ConnectionLockingDao(ISQLQueries ISQLQueries) {
            super(ISQLQueries);
        }

        public ConnectionLockingDao(ISQLQueries ISQLQueries, boolean lock) {
            super(ISQLQueries, lock);
        }

        public void lockWrite() {
            ISQLQueries.lockWrite();
        }

        public void lockRead() {
            ISQLQueries.lockRead();
        }

        public void unlockWrite() {
            ISQLQueries.unlockWrite();
        }

        public void unlockRead() {
            ISQLQueries.unlockRead();
        }
    }
}

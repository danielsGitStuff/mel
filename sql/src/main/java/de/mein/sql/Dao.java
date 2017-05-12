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


//    /**
//     * Created by xor on 11/25/16.
//     */
//    public static class LockingDao extends ConnectionLockingDao {
//
//        protected ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
//
//        public LockingDao(ISQLQueries ISQLQueries) {
//            super(ISQLQueries);
//        }
//
//        public LockingDao(ISQLQueries ISQLQueries, boolean lock) {
//            super(ISQLQueries, lock);
//        }
//
//        @Override
//        public void lockRead() {
//            lock.readLock().lock();
//        }
//
//        // todo debug
//        private Set<Thread> threads = new HashSet<>();
//
//        @Override
//        public void lockWrite() {
//            threads.add(Thread.currentThread());
//            lock.writeLock().lock();
//        }
//
//        @Override
//        public void unlockRead() {
//            lock.readLock().unlock();
//        }
//
//        @Override
//        public void unlockWrite() {
//            threads.remove(Thread.currentThread());
//            lock.writeLock().unlock();
//        }
//    }

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

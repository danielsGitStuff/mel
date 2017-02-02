package de.mein.sql;

/**
 * Created by xor on 30.10.2015.
 */
public class RWLock {
    private SimpleLock lock;
    private boolean read = false, write = false;
    private ReadLockCounter readLockCounter = new ReadLockCounter();


    public RWLock(){
        lock = new SimpleLock(this);
    }

    public synchronized RWLock lockRead() {
        if (write || !read)
            lock.lock();
        write = false;
        read = true;
        readLockCounter.inc();
        return this;
    }

    public synchronized RWLock unlockRead() {
        readLockCounter.dec();
        if (lock.isLocked() && readLockCounter.getReadLockCount() == 0) {
            lock.unlock();
            read = false;
        }
        return this;
    }

    public synchronized RWLock unlockWrite() {
        if (!read && write) {
            lock.unlock();
            write = false;
        }
        return this;
    }

    public synchronized RWLock lockWrite() {
        lock.lock();
        write = true;
        read = false;
        return this;
    }

    class ReadLockCounter {
        private int readLockCount = 0;

        public synchronized void inc() {
            readLockCount++;
        }

        public synchronized void dec() {
            readLockCount--;
        }

        public int getReadLockCount() {
            return readLockCount;
        }
    }

    class SimpleLock {

        private final RWLock rwLock;
        private boolean isLocked = false;

        SimpleLock(RWLock rwLock){
            this.rwLock = rwLock;
        }

        public synchronized void lock() {
            while (isLocked) {
                try {
                   rwLock.wait();
                } catch (InterruptedException e) {
                }
            }
            isLocked = true;
        }

        public boolean isLocked() {
            return isLocked;
        }

        public void unlock() {
            isLocked = false;
            rwLock.notify();
        }
    }
}

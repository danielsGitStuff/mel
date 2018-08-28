package de.mein.auth.tools;

/**
 * Created by xor on 6/7/17.
 */
public class CountLock {

    class Counter {

        private int count = 0;

        public synchronized int inc() {
            count++;
            return count;
        }

        public synchronized int dec() {
            count--;
            return count;
        }

        public synchronized int getCount() {
            return count;
        }

        public synchronized int incMax(int max) {
            count++;
            if (count > max)
                count = max;
            return count;
        }
    }

    private final Counter counter = new Counter();
    private SimpleLock lock = new SimpleLock();

    public CountLock lock() {
        if (counter.incMax(1) > 0) {
            lock.lock();
        }
        return this;
    }

    public CountLock unlock() {
        synchronized (counter) {
            //todo debug
            if (counter.getCount() == 2) {
                System.out.println("CountLock.unlock.debug");
            }
            if (counter.dec() < 1)
                lock.unlock();
        }
        return this;
    }


    public static class SimpleLock {

        private boolean isLocked = false;

        public synchronized void lock() {
            while (isLocked) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                }
            }
            isLocked = true;
        }

        public boolean isLocked() {
            return isLocked;
        }

        public synchronized void unlock() {
            isLocked = false;
            this.notify();
        }
    }
}

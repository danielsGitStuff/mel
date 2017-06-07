package de.mein.auth.tools;

import de.mein.sql.RWLock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xor on 6/7/17.
 */
public class CountLock {

    private AtomicInteger counter = new AtomicInteger(0);
    private RWLock accessLock = new RWLock();
    private RWLock lock = new RWLock();

    public CountLock lock() {
        accessLock.lockWrite();
        if (counter.incrementAndGet() > 1) {
            lock.lockWrite();
        }
        accessLock.unlockWrite();
        return this;
    }

    public CountLock unlock() {
        synchronized (counter) {
            counter.decrementAndGet();
            lock.unlockWrite();
        }
        return this;
    }
}

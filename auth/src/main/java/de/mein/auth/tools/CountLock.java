package de.mein.auth.tools;

import de.mein.sql.RWLock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by xor on 6/7/17.
 */
public class CountLock {

    private AtomicInteger counter = new AtomicInteger(0);
    private Lock accessLock = new ReentrantLock();
    private RWLock lock = new RWLock();

    public CountLock lock() {
        accessLock.lock();
        if (counter.incrementAndGet() > 1) {
            lock.lockWrite();
        }
        accessLock.unlock();
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

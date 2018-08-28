package de.mein.auth.tools;

import de.mein.sql.RWLock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xor on 21.08.2017.
 */
public class CountdownLock {
    private AtomicInteger counter;
    private RWLock accessLock = new RWLock();
    private RWLock lock = new RWLock();

    public CountdownLock(int countdown) {
        counter = new AtomicInteger(countdown);
    }

    public synchronized CountdownLock lock() {
        if (counter.get() > 0) {
            lock.lockWrite();
        }
        return this;
    }

    public CountdownLock unlock() {
        synchronized (counter) {
            int val = counter.decrementAndGet();
            if (val == 0)
                lock.unlockWrite();
        }
        return this;
    }
}

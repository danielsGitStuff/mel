package de.mel.auth.tools;

import de.mel.sql.RWLock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * There is a {@link java.util.concurrent.CountDownLatch} available. If CDL is available on Android too this class can be dropped.
 * TODO (check if alternative class is available on Android)
 * Created by xor on 21.08.2017.
 */
public class CountdownLock {
    private AtomicInteger counter;
    private RWLock accessLock = new RWLock();
    private RWLock lock = new RWLock().lockWrite();

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

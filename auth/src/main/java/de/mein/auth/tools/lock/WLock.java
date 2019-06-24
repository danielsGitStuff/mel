package de.mein.auth.tools.lock;

import java.util.concurrent.Semaphore;

import de.mein.auth.tools.WaitLock;

public class WLock {
    private Semaphore semaphore = new Semaphore(1, false);

    public synchronized WLock lock() throws InterruptedException {
        semaphore.acquire();
        return this;
    }

    public WLock unlock() {
        semaphore.release();
        return this;
    }


}
package de.mel.auth.tools;

import java.util.concurrent.Semaphore;

@Deprecated
/**
 * replaced by {@link CountWaitLock}
 * Created by xor on 5/3/17.
 */
public class WaitLock {
    private Semaphore semaphore = new Semaphore(1, true);

     public synchronized WaitLock lock() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return this;
    }

    public WaitLock unlock() {
        semaphore.release();
        return this;
    }
}

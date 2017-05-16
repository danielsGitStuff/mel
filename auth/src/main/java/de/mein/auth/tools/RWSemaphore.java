package de.mein.auth.tools;

import java.util.concurrent.Semaphore;

import static de.mein.sql.RWLock.printStack;

/**
 * Created by xor on 5/12/17.
 */
public class RWSemaphore {
    private Semaphore semaphore = new Semaphore(1, true);
    private boolean write = false;
    private ReadLockCounter readLockCounter = new ReadLockCounter();
    private boolean locked = false;

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

    public synchronized RWSemaphore lockRead() {
            if (write)
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            printStack("lockRead");
        readLockCounter.inc();
        return this;
    }


    public RWSemaphore unlockRead() {
            readLockCounter.dec();
            if (readLockCounter.getReadLockCount() == 0 && !write) {
                semaphore.release();
            printStack("unlockRead");
        }
        return this;
    }

    public synchronized RWSemaphore unlockWrite() {
            if (write) {
                semaphore.release();
                write = false;
            }
            printStack("unlockWrite");
        return this;
    }

    public synchronized RWSemaphore lockWrite() {
            try {
                semaphore.acquire();
                write = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            printStack("lockWrite");
        return this;
    }
}

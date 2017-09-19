package de.mein.thread;

import de.mein.auth.tools.CountLock;
import org.junit.Test;

public class LockTest {
    CountLock lock = new CountLock();
    private int ll = 0, uu = 0;

    @Test
    public void countLockTest() {
        lock = new CountLock();
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("LockTest.countLockTest.unlocking.1");
                unlock();
            }
        }).start();
        new Thread(() -> {
            try {
                System.out.println("LockTest.countLockTest.locking.2");
                lock();
                System.out.println("LockTest.countLockTest.unlocked.2");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        System.out.println("LockTest.countLockTest.locking.1");
        lock();
        unlock();
        System.out.println("LockTest.CountLockTest.end");
        unlock();
        unlock();
        System.out.println("LockTest.countLockTest.power off");
    }

    private void lock() {
        ll++;
        lock.lock();
    }

    private void unlock() {
        uu++;
        lock.unlock();
    }
}

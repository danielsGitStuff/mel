package de.mel.thread;

import de.mel.Lok;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.CountWaitLock;

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
                Lok.debug("LockTest.countLockTest.unlocking.1");
                unlock();
            }
        }).start();
        new Thread(() -> {
            try {
                Lok.debug("LockTest.countLockTest.locking.2");
                lock();
                Lok.debug("LockTest.countLockTest.unlocked.2");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        Lok.debug("LockTest.countLockTest.locking.1");
        lock();
        unlock();
        Lok.debug("LockTest.CountLockTest.end");
        unlock();
        unlock();
        Lok.debug("LockTest.countLockTest.power off");
    }

    @Test
    public void unlockDeferred() {
        lock = new CountLock();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
                Lok.debug("LockTest.countLockTest.locking.2");
                unlock();
                Lok.debug("LockTest.countLockTest.unlocked.2");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        Lok.debug("LockTest.countLockTest.locking.1");
        lock();
        lock();
        Lok.debug("LockTest.countLockTest.power off");
    }

    @Test
    public void unlockBefore() {
        lock = new CountLock();
        Lok.debug("LockTest.countLockTest.locking.1");
        unlock();
        lock();
        lock();
        Lok.debug("LockTest.countLockTest.power off");
    }

    @Test
    public void waitLockUnlockDeferred() {
        lock = new CountWaitLock();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
                Lok.debug("LockTest.countLockTest.locking.2");
                unlock();
                Lok.debug("LockTest.countLockTest.unlocked.2");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        Lok.debug("LockTest.countLockTest.locking.1");
        lock();
        Lok.debug("LockTest.countLockTest.power off");
    }


    @Test
    public void waitLockUnlockBefore() {
        lock = new CountWaitLock();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
                Lok.debug("LockTest.countLockTest.locking.2");
                unlock();
                Lok.debug("LockTest.countLockTest.unlocked.2");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        Lok.debug("LockTest.countLockTest.locking.1");
        unlock();
        lock();
        Lok.debug("LockTest.countLockTest.power off");
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

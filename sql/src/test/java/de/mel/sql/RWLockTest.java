package de.mel.sql;

import de.mel.testing.LockTest;
import de.mel.testing.TestRunnable;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RWLockTest extends LockTest {

    protected RWLock lock;

    @Override
    public void beforeImpl() {
        lock = new RWLock();
        RWLock.PRINT_STACK = false;
    }

    @Test
    public void readWrite() {
        TestRunnable tRead1 = () -> {
            lock.lockRead();
            reach1.set(true);
        };
        TestRunnable tRead2 = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            lock.lockWrite();
            reach2.set(true);
            System.out.println("got write lock");
        };
        killAfter(200, tRead1, tRead2);
        assertTrue(reach1.get());
        assertFalse(reach2.get());
    }

    @Test
    public void readRead() {
        TestRunnable tRead1 = () -> {
            lock.lockRead();
            reach1.set(true);
        };
        TestRunnable tRead2 = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            lock.lockRead();
            reach2.set(true);
            System.out.println("got read lock");
        };
        killAfter(200, tRead1, tRead2);
        assertTrue(reach1.get());
        assertTrue(reach2.get());
    }

    @Test
    public void writeRead() {
        TestRunnable tRead1 = () -> {
            lock.lockWrite();
            reach1.set(true);
        };
        TestRunnable tRead2 = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            lock.lockRead();
            reach2.set(true);
            System.out.println("got write lock");
        };
        killAfter(200, tRead1, tRead2);
        assertTrue(reach1.get());
        assertFalse(reach2.get());
    }

    @Test
    public void writeWrite() {
        TestRunnable tRead1 = () -> {
            lock.lockWrite();
            reach1.set(true);
        };
        TestRunnable tRead2 = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            lock.lockWrite();
            reach2.set(true);
            System.out.println("got write lock");
        };
        killAfter(200, tRead1, tRead2);
        assertTrue(reach1.get());
        assertFalse(reach2.get());
    }

}

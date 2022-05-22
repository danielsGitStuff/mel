package de.mel.auth.tools;


import de.mel.Lok;
import de.mel.auth.tools.lock2.Prison;
import de.mel.auth.tools.lock2.PrisonKey;
import de.mel.auth.tools.lock2.Read;
import de.mel.testing.LockTest;
import de.mel.testing.TestRunnable;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrisonTest extends LockTest {
    protected String lockable1;
    protected String lockable2;

    @Override
    public void beforeImpl() {
        lockable1 = "lockable 1";
        lockable2 = "lockable 2";
    }

    @Test
    public void intersection1() {
        TestRunnable t1 = () -> {
            Lok.debug("locking A /1");
            PrisonKey key1 = Prison.confine(lockable1, Prison.read(lockable2));
            key1.lock();
            Lok.debug("locking A /1 successful");
            reach1.set(true);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            Lok.debug("locking A /2");
            PrisonKey key2 = Prison.confine(lockable1);
            key2.lock();
            Lok.debug("locking A /2 successful");
            reach2.set(true);
        };

        killAfter(200, t1, t2);
        assertTrue(reach1.get());
        assertFalse(reach2.get());
    }

    @Test
    public void intersection2() {
        TestRunnable t1 = () -> {
            Lok.debug("locking A /1");
            PrisonKey key1 = Prison.confine(lockable1, Prison.read(lockable2));
            key1.lock();
            Lok.debug("locking A /1 successful");
            reach1.set(true);
            Thread.sleep(150);
            Lok.debug("ending A /1");
            key1.end();
            Lok.debug("ending A /1 successful");
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            Lok.debug("locking A /2");
            PrisonKey key2 = Prison.confine(lockable1);
            key2.lock();
            Lok.debug("locking A /2 successful");
            reach2.set(true);
        };
        TestRunnable t3 = () -> {
            Thread.sleep(100);
            Lok.debug("locking A /3");
            PrisonKey key3 = Prison.confine(lockable1);
            key3.lock();
            Lok.debug("locking A /3 successful");
            reach3.set(true);
        };

        killAfter(-300, t1, t2, t3);
        assertTrue(reach1.get());
        assertTrue(Boolean.logicalXor(reach2.get(), reach3.get()));
    }
}

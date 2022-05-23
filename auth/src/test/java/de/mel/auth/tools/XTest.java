package de.mel.auth.tools;

import de.mel.Lok;
import de.mel.auth.tools.lock3.BunchOfLocks;
import de.mel.auth.tools.lock3.P;
import de.mel.testing.LockTest;
import de.mel.testing.TestRunnable;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XTest extends LockTest {

    private String locker1, locker2;
    private BunchOfLocks bol1, bol2, bol3, bol4;

    @Override
    public void beforeImpl() {
        locker1 = "l 1";
        locker2 = "l 2";
        bol1 = null;
        bol2 = null;
        bol3 = null;
        bol4 = null;
    }

    @After
    public void after() {
        if (bol1 != null)
            P.end(bol1);
        if (bol2 != null)
            P.end(bol2);
        if (bol3 != null)
            P.end(bol3);
        if (bol4 != null)
            P.end(bol4);
    }

    @Test
    public void readRead() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(P.read(locker1));
            P.access(bol1);
            reach1.set(true);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(P.read(locker1));
            P.access(bol2);
            reach2.set(true);
        };
        killAfter(200, t1, t2);
        assertTrue(reach1.get());
        assertTrue(reach2.get());
    }

    @Test
    public void readWrite() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(P.read(locker1));
            P.access(bol1);
            reach1.set(true);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(locker1);
            P.access(bol2);
            reach2.set(true);
        };
        killAfter(200, t1, t2);
        assertTrue(reach1.get());
        assertFalse(reach2.get());
    }

    @Test
    public void writeRead() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(locker1);
            P.access(bol1);
            reach1.set(true);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(P.read(locker1));
            P.access(bol2);
            reach2.set(true);
        };
        killAfter(200, t1, t2);

        assertTrue(reach1.get());
        assertFalse(reach2.get());
    }

    @Test
    public void writeWrite() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(locker1);
            P.access(bol1);
            reach1.set(true);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(locker1);
            P.access(bol2);
            reach2.set(true);
        };
        killAfter(200, t1, t2);
        assertTrue(reach1.get());
        assertFalse(reach2.get());
    }

    @Test
    public void mixedReadReadVsNoneWrite() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(P.read(locker1, locker2));
            P.access(bol1);
            reach1.set(true);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(locker2);
            P.access(bol2);
            reach2.set(true);
        };
        killAfter(200, t1, t2);
        assertTrue(reach1.get());
        assertFalse(reach2.get());
    }

    /**
     * t1, t3 finish. t2 does not cause it depends on t1, which does not exit.
     */
    @Test
    public void mixed1a() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(P.read(locker1, locker2)).setName("A");
            P.access(bol1);
            reach1.set(true);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(locker2).setName("B");
            P.access(bol2);
            reach2.set(true);
        };
        TestRunnable t3 = () -> {
            Thread.sleep(80);
            bol3 = P.confine(P.read(locker2)).setName("C");
            P.access(bol3);
            reach3.set(true);
        };
        killAfter(200, t1, t2, t3);
        assertTrue(reach1.get());
        assertFalse(reach2.get());
        assertTrue(reach3.get());
    }

    /**
     * all finish. t2 waits for t1 and t3
     */
    @Test
    public void mixed1b() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(P.read(locker1, locker2)).setName("A");
            P.access(bol1);
            reach1.set(true);
            P.exit(bol1);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(locker2).setName("B");
            P.access(bol2);
            reach2.set(true);
        };
        TestRunnable t3 = () -> {
            Thread.sleep(80);
            bol3 = P.confine(P.read(locker2)).setName("C");
            P.access(bol3);
            reach3.set(true);
            P.exit(bol3);
        };
        killAfter(200, t1, t2, t3);
        assertTrue(reach1.get());
        assertTrue(reach2.get());
        assertTrue(reach3.get());
    }

    /**
     * t1 exits, t3 waits for t2
     */
    @Test
    public void mixed2() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(P.read(locker1, locker2)).setName("A");
            P.access(bol1);
            reach1.set(true);
            P.exit(bol1);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(locker2).setName("B");
            P.access(bol2);
            reach2.set(true);
        };
        TestRunnable t3 = () -> {
            Thread.sleep(120);
            bol3 = P.confine(P.read(locker2)).setName("C");
            P.access(bol3);
            reach3.set(true);
        };
        killAfter(200, t1, t2, t3);
        assertTrue(reach1.get());
        assertTrue(reach2.get());
        assertFalse(reach3.get());
    }

    /**
     * t1, t2, t3 finish in that order, no one waits for another {@link Thread}
     */
    @Test
    public void mixed3() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(P.read(locker1, locker2)).setName("A");
            P.access(bol1);
            reach1.set(true);
            P.exit(bol1);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(locker2).setName("B");
            P.access(bol2);
            reach2.set(true);
        };
        TestRunnable t3 = () -> {
            Thread.sleep(120);
            bol3 = P.confine(P.read(locker1)).setName("C");
            P.access(bol3);
            reach3.set(true);
        };
        killAfter(200, t1, t2, t3);
        assertTrue(reach1.get());
        assertTrue(reach2.get());
        assertTrue(reach3.get());
    }

    /**
     * t2 and t3 wait for t1
     */
    @Test
    public void mixed4() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(locker1, locker2).setName("A");
            P.access(bol1);
            reach1.set(true);
            Thread.sleep(120);
            P.exit(bol1);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(P.read(locker1, locker2)).setName("B");
            P.access(bol2);
            reach2.set(true);
        };
        TestRunnable t3 = () -> {
            Thread.sleep(150);
            bol3 = P.confine(P.read(locker1)).setName("C");
            P.access(bol3);
            reach3.set(true);
        };
        killAfter(200, t1, t2, t3);
        assertTrue(reach1.get());
        assertTrue(reach2.get());
        assertTrue(reach3.get());
    }

    /**
     * tests write lock ownership and change
     */
    @Test
    public void mixed5() {
        TestRunnable t1 = () -> {
            bol1 = P.confine(locker1, locker2).setName("A");
            P.access(bol1);
            reach1.set(true);
            Thread.sleep(120);
            P.exit(bol1);
        };
        TestRunnable t2 = () -> {
            Thread.sleep(100);
            bol2 = P.confine(locker1, locker2).setName("B");
            P.access(bol2);
            reach2.set(true);
        };
        killAfter(200, t1, t2);
        assertTrue(reach1.get());
        assertTrue(reach2.get());
    }
}

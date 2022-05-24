package de.mel.concurrency;

import de.mel.auth.tools.N;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.auth.tools.lock2.LockObjectEntry;
import de.mel.auth.tools.lock2.P;
import de.mel.testing.LockTest;
import de.mel.testing.TestRunnable;
import org.junit.After;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class GlobalLockingTest extends LockTest {

    private String locker1, locker2;
    private BunchOfLocks bol1, bol2, bol3, bol4;

    @Override
    public void beforeImpl() {
        P.enableDebugPrinting();
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
    public void mixed1() {
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
    public void mixed6() {
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

    private static Map<Object, LockObjectEntry> getLockObjectEntriesInstanceMap() throws NoSuchFieldException, IllegalAccessException {
        Map<Object, LockObjectEntry> instances = (Map<Object, LockObjectEntry>) N.reflection.getStaticProperty(LockObjectEntry.class, "INSTANCES", Map.class);
        return instances;
    }

    @Test
    public void free1() throws NoSuchFieldException, IllegalAccessException {
        Map<Object, LockObjectEntry> instances = GlobalLockingTest.getLockObjectEntriesInstanceMap();
        assertEquals(0, instances.size());
        bol1 = P.confine(locker1);
        assertEquals(1, instances.size());
        bol1.end();
        assertEquals(0, instances.size());
    }

    @Test
    public void free2() throws NoSuchFieldException, IllegalAccessException {
        Map<Object, LockObjectEntry> instances = GlobalLockingTest.getLockObjectEntriesInstanceMap();
        assertEquals(0, instances.size());
        bol1 = P.confine(locker1);
        bol2 = P.confine(locker2);
        assertEquals(2, instances.size());
        bol1.end();
        bol2.end();
        assertEquals(0, instances.size());
    }

    @Test
    public void free3() throws NoSuchFieldException, IllegalAccessException {
        Map<Object, LockObjectEntry> instances = GlobalLockingTest.getLockObjectEntriesInstanceMap();
        assertEquals(0, instances.size());
        bol1 = P.confine(locker1, locker2);
        assertEquals(2, instances.size());
        bol1.end();
        assertEquals(0, instances.size());
    }
}

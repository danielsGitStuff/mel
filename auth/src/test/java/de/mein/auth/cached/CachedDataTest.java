package de.mein.auth.cached;

import de.mein.Lok;
import de.mein.auth.data.cached.*;
import de.mein.auth.tools.F;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

public class CachedDataTest {
    public static class A implements SerializableEntity {
        private String name;

        public A(String name) {
            this.name = name;
        }

        public A() {

        }

        static A n(String name) {
            return new A(name);
        }
    }

    private CachedList<A> iterable;
    private CachedList<A> emptyIterable;
    private final File cacheDir = new File("cache.test");
    private final File cache1 = new File(cacheDir, "c1");
    private final File cache2 = new File(cacheDir, "c2");
    private final int PART_SIZE = 5;
    private final Long CACHE_ID = 666L;
    private Field partsMissedField;


    @Test
    public void iterateNoDisk() throws Exception {
        final int SIZE = PART_SIZE - 1;
        fill(SIZE);
        iterable.toDisk();
        emptyIterable.initPartsMissed(1);
        emptyIterable.onReceivedPart(iterable.getPart(1));
        Iterator<A> iterator = emptyIterable.iterator();
        int countdown = SIZE;
        while (iterator.hasNext()) {
            A a = iterator.next();
            Lok.debug(a.name);
            countdown--;
        }
        assertEquals(0, countdown);
        Lok.debug("");
    }

    @Test
    public void iterateWithDisk() throws Exception {
        final int SIZE = PART_SIZE + 1;
        fill(SIZE);
        iterable.toDisk();
        emptyIterable.initPartsMissed(2);
        emptyIterable.onReceivedPart(iterable.getPart(1));
        emptyIterable.onReceivedPart(iterable.getPart(2));
        Iterator<A> iterator = emptyIterable.iterator();
        int countdown = SIZE;
        while (iterator.hasNext()) {
            A a = iterator.next();
            Lok.debug(a.name);
            countdown--;
        }
        assertEquals(0, countdown);
        Lok.debug("");
    }


    @Before
    public void before() throws NoSuchFieldException {
        F.rmRf(cacheDir);
        cacheDir.mkdirs();
        cache1.mkdirs();
        cache2.mkdirs();
        iterable = new CachedList<>(cache1, CACHE_ID, PART_SIZE);
        emptyIterable = new CachedList<>(cache2, CACHE_ID, PART_SIZE);
        partsMissedField = CachedInitializer.class.getDeclaredField("partsMissed");
        partsMissedField.setAccessible(true);
    }

    private void fill(int amount) throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        for (Integer i = 0; i < amount; i++)
            iterable.add(A.n("n." + i.toString()));
    }

    @Test
    public void partCountOneLess() throws Exception {
        fill(PART_SIZE - 1);
        assertEquals(1, iterable.getPartCount());
    }

    @Test
    public void partCountMax() throws Exception {
        fill(PART_SIZE);
        assertEquals(1, iterable.getPartCount());
    }

    @Test
    public void partCountOneMore() throws Exception {
        fill(PART_SIZE + 1);
        assertEquals(2, iterable.getPartCount());
    }

    @Test
    public void diskOneMore() throws Exception {
        fill(PART_SIZE + 1);
        iterable.toDisk();
        checkFiles(cache1, 2);
    }

    @Test
    public void missingParts() throws Exception {
        fill(2 * PART_SIZE + 1);
        iterable.toDisk();
        checkFiles(cache1, 3);
        Lok.debug("");
        emptyIterable.initPartsMissed(3);
        N.forLoop(1, 4, (stoppable, index) -> {
            CachedPart part = iterable.getPart(index);
            emptyIterable.onReceivedPart(part);
        });
        Lok.debug("");
    }

    @Test
    public void initParts() throws Exception {
        iterable.toDisk();
        emptyIterable.initPartsMissed(3);
        Set<Integer> partsMissed = (Set<Integer>) partsMissedField.get(emptyIterable);
        for (Integer i = 1; i <= 3; i++) {
            assertTrue(partsMissed.remove(i));
        }
        assertTrue(partsMissed.isEmpty());
        Lok.debug("done");
    }

    @Test
    public void missingPartsRequested() throws Exception {
        fill(2 * PART_SIZE + 1);
        iterable.toDisk();
        checkFiles(cache1, 3);
        Lok.debug("");
        emptyIterable.initPartsMissed(3);
        while (!emptyIterable.isComplete()) {
            int partNumber = emptyIterable.getNextPartNumber();
            CachedPart part = iterable.getPart(partNumber);
            emptyIterable.onReceivedPart(part);
        }
        Lok.debug("");
    }

    private void checkFiles(File cacheDir, Integer expectedParts) {
        for (Integer i = 1; i <= expectedParts; i++) {
            File f = new File(cacheDir, CACHE_ID + "." + i.toString() + ".json");
            assertTrue(f.exists());
        }
        File f = new File(cacheDir, CACHE_ID + "." + Integer.toString(expectedParts + 1) + ".json");
        assertFalse(f.exists());
    }

    @After
    public void after() {
        F.rmRf(cacheDir);
    }
}

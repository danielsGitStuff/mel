package de.mein.auth.cached;

import de.mein.Lok;
import de.mein.auth.data.cached.CachedData;
import de.mein.auth.data.cached.CachedIterable;
import de.mein.auth.data.cached.CachedPart;
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

    private CachedIterable<A> iterable;
    private CachedIterable<A> emptyIterable;
    private final File cacheDir = new File("cache.test");
    private final File cache1 = new File(cacheDir, "c1");
    private final File cache2 = new File(cacheDir, "c2");
    private final int PART_SIZE = 5;
    private final Long CACHE_ID = 666L;
    private Field partsMissedField;

    private void rmRf(File dir) {
        if (dir.exists()) {
            N.forEach(dir.listFiles((dir1, name) -> dir1.isDirectory()), this::rmRf);
            N.forEach(dir.listFiles((dir1, name) -> dir1.isFile()), file -> file.delete());
            dir.delete();
        }
    }


    @Before
    public void before() throws NoSuchFieldException {
        rmRf(cacheDir);
        cacheDir.mkdirs();
        cache1.mkdirs();
        cache2.mkdirs();
        iterable = new CachedIterable<>(cache1, PART_SIZE);
        iterable.setCacheId(CACHE_ID);
        emptyIterable = new CachedIterable<>(cache2, PART_SIZE);
        emptyIterable.setCacheId(CACHE_ID);
        partsMissedField = CachedData.class.getDeclaredField("partsMissed");
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
        rmRf(cacheDir);
    }
}

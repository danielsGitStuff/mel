package de.mein.core.serialize.data;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.TypeVariable;

public class CachedIterableTest {
    private static File CACHE_DIR = new File("testcache");
    private static CachedIterable<Integer> iterable;

    @Before
    public void before() {
        CACHE_DIR.mkdirs();
        iterable = new CachedIterable(CACHE_DIR, "test.name", 5);
        System.out.println("CachedIterableTest.before.done: " + CACHE_DIR.getAbsolutePath());
    }

    //@After
    public void after() throws IOException {
        delete(CACHE_DIR);
    }

    private static void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }

    @Test
    public void serialize() throws IllegalAccessException, IOException, JsonSerializationException {
        for (Integer i = 0; i < 20; i++) {
            iterable.add(i);
        }
    }
}

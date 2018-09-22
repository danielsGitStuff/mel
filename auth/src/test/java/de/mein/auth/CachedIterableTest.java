package de.mein.auth;

import de.mein.Lok;
import de.mein.auth.data.cached.data.CachedIterable;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CachedIterableTest {
    private static File CACHE_DIR = new File("testcache");
    private static CachedIterable iterable;


    @Before
    public void before() {
        CACHE_DIR.mkdirs();
        iterable = new CachedIterable(CACHE_DIR, 5);
        iterable.setCacheId(99999L);
        Lok.debug("CachedIterableTest.before.done: " + CACHE_DIR.getAbsolutePath());
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
    public void serialize() throws IllegalAccessException, IOException, JsonSerializationException, NoSuchMethodException, InvocationTargetException, InstantiationException {
        for (Integer i = 1; i <= 21; i++) {
            iterable.add(new SimpleSerializableEntity().setPrimitive(i.toString()));
        }
        Lok.debug("CachedIterableTest.serialize.done");
    }

    @Test
    public void iterate() throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        serialize();
        Iterator<SimpleSerializableEntity> iterator = iterable.iterator();
        assertTrue(iterator.hasNext());
        while (iterator.hasNext()) {
            Lok.debug("CachedIterableTest.iterate: " + iterator.next().getPrimitive());
        }
        assertTrue(iterable.createCachedPartFile(1).exists());
        iterable.cleanUp();
        assertFalse(iterable.createCachedPartFile(1).exists());
        Lok.debug("CachedIterableTest.iterate.done");
    }

    /**
     * Created by xor on 12/21/15.
     */
    public static class SimpleSerializableEntity implements SerializableEntity {
        public SimpleSerializableEntity in;
        public SimpleSerializableEntity out;
        private String primitive;

        public SimpleSerializableEntity setPrimitive(String primitive) {
            this.primitive = primitive;
            return this;
        }

        public String getPrimitive() {
            return primitive;
        }
    }

}

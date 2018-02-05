package de.mein.core.serialize.data;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

/**
 * Data structure which caches its elements to disk once it has reached its maximum size.
 */
public class CachedIterable<T extends SerializableEntity> extends CachedData implements Iterable<T> {

    @JsonIgnore
    private long size = 0;
    protected int partSize;


    public CachedIterable(File cacheDir, String name, int partSize) {
        super(name);
        this.cacheDir = cacheDir;
        this.part = new CachedListPart(name, 0, partSize);
    }

    public CachedIterable() {
    }

    @Override
    public void setCacheDirectory(File cacheDirectory) {
        this.cacheDir = cacheDirectory;
    }

    public void add(T elem) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        if (part.size() > partSize) {
            serializePart();
            createNewPart();
        }
        part.add(elem);
        size++;
    }

    protected void createNewPart() {
        part = new CachedListPart(name, partCount, partSize);
        partCount++;
    }

    /**
     * writes everything to disk to save memory.
     * call this when done with adding all elements.
     */
    public void toDisk() throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        serializePart();
    }

    /**
     * removes all cached files from disk.
     */
    public void cleanUp() {
        for (Integer i = 1; i <= partCount; i++) {
            File file = createCachedPartFile(i);
            file.delete();
        }
    }


    @Override
    public Iterator<T> iterator() {
        try {
            // first check if there is a not serialized part in memory
            serializePart();
            return new CachedIterator(this);
        } catch (JsonSerializationException | IllegalAccessException | IOException | NoSuchMethodException
                | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    public long getSize() {
        return size;
    }

    /**
     * loads the first {@link CachedListPart}
     */
    public void loadFirstCached() throws IOException, JsonDeserializationException {
        if (partCount > 1) {
            File file = createCachedPartFile(1);
            part = CachedListPart.read(file);
        }
    }
}

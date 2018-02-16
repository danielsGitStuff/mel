package de.mein.core.serialize.data;

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

    private long size = 0;


    public CachedIterable(File cacheDir, int partSize) {
        super(partSize);
        this.cacheDir = cacheDir;
        this.part = new CachedListPart(cacheId, 1, partSize);
    }

    public CachedIterable() {
    }

    public void add(T elem) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        //todo debug
        if (part == null){
            System.out.println("CachedIterable.add.debug");
        }
        if (part.size() >= partSize) {
            part.setSerialized();
            write(part);
            createNewPart();
        }
        part.add(elem);
        size++;
    }

    protected void createNewPart() {
        partCount++;
        part = new CachedListPart(cacheId, partCount, partSize);
    }

    /**
     * writes everything to disk to save memory.
     * call this when done with adding all elements.
     */
    public void toDisk() throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        write(part);
    }

    /**
     * removes all cached files from disk.
     */
    public void cleanUp() {
        for (Integer i = 1; i <= partCount; i++) {
            try {
                File file = createCachedPartFile(i);
                file.delete();
            } catch (Exception e) {
                System.err.println("CachedIterable.cleanUp.err(cacheId= " + cacheId + " part= " + part + " cacheDir.null= " + (cacheDir == null)+")");
                e.printStackTrace();
            }
        }
    }


    @Override
    public Iterator<T> iterator() {
        try {
            // first check if there is a not serialized part in memory
            if (!part.isSerialized())
                write(part);
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
            part = CachedPart.read(file);
        }
    }
}

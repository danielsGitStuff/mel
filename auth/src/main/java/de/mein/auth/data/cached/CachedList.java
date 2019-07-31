package de.mein.auth.data.cached;

import de.mein.Lok;
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
public class CachedList<T extends SerializableEntity> extends CachedInitializer<CachedListPart> implements Iterable<T> {
    @JsonIgnore
    private long size = 0;


    public CachedList(File cacheDir, long cacheId, int partSize) {
        super(cacheDir, cacheId, partSize);
    }

    public CachedList() {
    }

    public void add(T elem) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        if (partCount == 0)
            partCount = 1;
        if (part == null) {
            this.part = new CachedListPart(cacheId, partCount, partSize);
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
    @Override
    public void toDisk() throws JsonSerializationException, IOException {
        if (part == null) {
            this.part = new CachedListPart(cacheId, partCount, partSize);
        }
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
                Lok.error("CachedList.cleanUp.err(cacheId= " + cacheId + " part= " + part + " cacheDir.null= " + (cacheDir == null) + ")");
                e.printStackTrace();
            }
        }
    }


    @Override
    public Iterator<T> iterator() {
        try {
            // first check if there is a not serialized part in memory
            if (part != null && !part.isSerialized())
                write(part);
            return new CachedIterator(this);
        } catch (JsonSerializationException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onReceivedPart(CachedPart cachedPart) throws JsonSerializationException, IOException {
        if (cachedPart != null) {
            super.onReceivedPart(cachedPart);
            size += cachedPart.size();
        }
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
            part = (CachedListPart) CachedPart.read(file);
        }
    }
}

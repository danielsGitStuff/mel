package de.mein.core.serialize.data;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

/**
 * Data structure which caches its elements to disk once it has reached its maximum size.
 *
 */
public class CachedIterable<T extends SerializableEntity> implements SerializableEntity, Iterable<T> {

    private final File cacheDir;
    private final String name;
    private final int partSize;
    private int partCount = 1;
    private long size = 0;

    public long getSize() {
        return size;
    }

    protected CachedPart part;

    public CachedIterable(File cacheDir, String name, int partSize) {
        this.cacheDir = cacheDir;
        this.name = name;
        this.partSize = partSize;
        this.part = new CachedPart(partSize);
    }

    public void add(SerializableEntity elem) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        serializePart(false);
        part.add(elem);
        size++;
    }

    /**
     * serializes a {@link CachedPart} when necessary.
     */
    private void serializePart(boolean serializeAnyway) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InvocationTargetException, InstantiationException {
        if (part.size() >= partSize || serializeAnyway) {
            //serialize actual list, create a new one
            String json = SerializableEntitySerializer.serialize(part);
            //save to file
            File file = createCachedPartFile(partCount);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            out.write(json.getBytes());
            out.close();
            //create a new part if still adding things
            if (serializeAnyway) {
                part = null;
            } else {
                part = new CachedPart(partSize);
                partCount++;
            }
        }
    }


    public File createCachedPartFile(int partCount) {
        return new File(cacheDir.getAbsolutePath() + File.separator + name + "." + partCount + ".json");
    }

    /**
     * writes everything to disk to save memory.
     * call this when done with adding all elements.
     */
    public void toDisk() throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        serializePart(true);
    }

    /**
     * removes all cached files from disk.
     */
    public void cleanUp(){
        for (Integer i = 1; i <= partCount; i++){
            File file = createCachedPartFile(i);
            file.delete();
        }
    }


    @Override
    public Iterator<T> iterator() {
        try {
            // first check if there is a not serialized part in memory
            serializePart(true);
            return new CachedIterator(this);
        } catch (JsonSerializationException | IllegalAccessException | IOException | NoSuchMethodException
                | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

}

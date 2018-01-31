package de.mein.core.serialize.data;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * data structure which caches its elements to disk once it has reached its maximum size.
 *
 * @param <T> either simple types (int, Boolean...) or {@link SerializableEntity}s
 */
public class CachedIterable<T> implements SerializableEntity, Iterable<T> {

    private class CachedPart<T> implements SerializableEntity {
        private List<T> elements;
        @JsonIgnore
        private boolean serialized = false;

        private CachedPart(int max) {
            elements = new ArrayList<>(max);
        }

        private int size() {
            return elements.size();
        }

        public boolean isSerialized() {
            return serialized;
        }

        private void add(T elem) {
            elements.add(elem);
        }
    }

    private final File cacheDir;
    @JsonIgnore
    private final String name;
    @JsonIgnore
    private final int max;
    private int partCount = 1;


    private CachedPart<T> part;

    public CachedIterable(File cacheDir, String name, int max) {
        this(cacheDir, name, max, 0);
    }

    private CachedIterable(File cacheDir, String name, int max, int count) {
        this.cacheDir = cacheDir;
        this.name = name;
        this.max = max;
        this.part = new CachedPart(max);
    }

    public void add(T elem) throws JsonSerializationException, IllegalAccessException, IOException {
        serializePart(false);
        part.add(elem);
    }

    /**
     * serializes a {@link CachedPart} when necessary.
     */
    private void serializePart(boolean serializeAnyway) throws JsonSerializationException, IllegalAccessException, IOException {
        if (part.size() > max || serializeAnyway) {
            //serialize actual list, create a new one
            String json = SerializableEntitySerializer.serialize(part);
            //save to file
            String fileName = name + "." + partCount + ".json";
            File file = new File(cacheDir.getAbsolutePath() + File.separator + fileName);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            out.write(json.getBytes());
            out.close();
            //create a new part
            part = new CachedPart<>(max);
            partCount++;
        }
    }

    @Override
    public Iterator<T> iterator() {
        try {
            // first check if there is a not serialized part in memory
            serializePart(true);
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public T next() {
                    return null;
                }
            };
        } catch (JsonSerializationException | IllegalAccessException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void forEach(Consumer<? super T> action) {

    }

    @Override
    public Spliterator<T> spliterator() {
        return null;
    }


}

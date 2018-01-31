package de.mein.core.serialize.data;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

public class CachedIterator<T extends  SerializableEntity> implements Iterator<T> {

    private CachedIterable iterable;
    private int pos = 0;
    private int partCount = 1;
    private Iterator<SerializableEntity> partIterator;

    public CachedIterator(CachedIterable iterable) {
        this.iterable = iterable;
    }

    @Override
    public boolean hasNext() {
        return pos < iterable.getSize();
    }

    @Override
    public T next() {
        try {
            pos++;
            if (partIterator == null || !partIterator.hasNext()) {
                //read
                File file = iterable.createCachedPartFile(partCount);
                partCount++;
                byte[] bytes = new byte[(int) file.length()];
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
                in.read(bytes);
                in.close();
                String json = new String(bytes);
                //deserialize
                CachedPart part = (CachedPart) SerializableEntityDeserializer.deserialize(json);
                partIterator = part.getElements().iterator();
            }
            return (T) partIterator.next();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}

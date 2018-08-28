package de.mein.auth.data.cached.data;

import de.mein.core.serialize.SerializableEntity;

import java.io.File;
import java.util.Iterator;

public class CachedIterator<T extends  SerializableEntity> extends CachedData implements Iterator<T> {

    private CachedIterable iterable;
    private int pos = 0;
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
                CachedListPart part = (CachedListPart) CachedPart.read(file);
                partIterator = part.getElements().iterator();
            }
            return (T) partIterator.next();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}

package de.mein.core.serialize.data;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CachedListPart extends CachedPart {
    private List<SerializableEntity> elements = new ArrayList<>();


    public CachedListPart(Long cacheId, int partNumber, int max) {
        super(cacheId, partNumber);
        elements = new ArrayList<>(max);
    }

    /**
     * for deserialization purposes
     */
    public CachedListPart() {
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public void add(SerializableEntity elem) {
        elements.add(elem);
    }

    public List<SerializableEntity> getElements() {
        return elements;
    }


}

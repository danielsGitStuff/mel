package de.mel.auth.data.cached;

import de.mel.core.serialize.SerializableEntity;

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

    public void add(SerializableEntity elem) {
        elements.add(elem);
    }

    public List<SerializableEntity> getElements() {
        return elements;
    }


}

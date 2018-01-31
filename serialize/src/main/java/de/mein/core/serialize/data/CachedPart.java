package de.mein.core.serialize.data;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

public class CachedPart implements SerializableEntity {
    private List<SerializableEntity> elements = new ArrayList<>();
    @JsonIgnore
    private boolean serialized = false;

    CachedPart(int max) {
        elements = new ArrayList<>(max);
    }

    /**
     * for deserialization purposes
     */
    public CachedPart(){
    }

    int size() {
        return elements.size();
    }

     boolean isSerialized() {
        return serialized;
    }

    void add(SerializableEntity elem) {
        elements.add(elem);
    }

    public List<SerializableEntity> getElements() {
        return elements;
    }
}

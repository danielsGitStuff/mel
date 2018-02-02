package de.mein.core.serialize.data;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

public class CachedPart implements CachedData {
    private List<SerializableEntity> elements = new ArrayList<>();
    private int partNumber;
    private String name;

    CachedPart(int partNumber,int max) {
        elements = new ArrayList<>(max);
        this.partNumber = partNumber;
    }

    /**
     * for deserialization purposes
     */
    public CachedPart(){
    }

    int size() {
        return elements.size();
    }

    void add(SerializableEntity elem) {
        elements.add(elem);
    }

    public List<SerializableEntity> getElements() {
        return elements;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public String getName() {
        return name;
    }
}

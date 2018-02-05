package de.mein.core.serialize.data;

import de.mein.core.serialize.SerializableEntity;

/**
 * A single part which is serialized to disk.
 */
public abstract class CachedPart implements SerializableEntity {
    private int partNumber;
    private String name;

    public CachedPart(String name, int partNumber) {
        this.partNumber = partNumber;
        this.name = name;
    }

    public CachedPart() {

    }

    public int getPartNumber() {
        return partNumber;
    }

    public String getName() {
        return name;
    }

    public abstract <T extends SerializableEntity> void add(T elem);

    public abstract int size();

}

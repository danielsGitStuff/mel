package de.mein.auth.data.cached;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * A single part which is serialized to disk.
 */
@Deprecated
public abstract class CachedPartOlde implements SerializableEntity {
    private int partNumber;
    private Long cacheId;
    @JsonIgnore
    private boolean serialized = false;

    public CachedPartOlde(Long cacheId, int partNumber) {
        this.partNumber = partNumber;
        this.cacheId = cacheId;
    }

    public CachedPartOlde() {

    }

    public CachedPartOlde setCacheId(Long cacheId) {
        this.cacheId = cacheId;
        return this;
    }

    public static CachedPartOlde read(File file) throws IOException, JsonDeserializationException {
        byte[] bytes = new byte[(int) file.length()];
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
        in.read(bytes);
        in.close();
        String json = new String(bytes);
        //deserialize
        CachedPartOlde part = (CachedPartOlde) SerializableEntityDeserializer.deserialize(json);
        part.setSerialized();
        return part;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public Long getCacheId() {
        return cacheId;
    }

    public abstract <T extends SerializableEntity> void add(T elem);

    public abstract int size();

    public CachedPartOlde setSerialized() {
        this.serialized = true;
        return this;
    }

    public boolean isSerialized() {
        return serialized;
    }
}

package de.mein.auth.data;

import de.mein.core.serialize.SerializableEntity;

public class AbstractCachedMessage<T extends AbstractCachedMessage> implements SerializableEntity {
    private Long cacheId;

    public T setCacheId(Long cacheId) {
        this.cacheId = cacheId;
        return (T) this;
    }

    public Long getCacheId() {
        return cacheId;
    }
}

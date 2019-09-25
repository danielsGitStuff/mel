package de.mel.auth.data;

import de.mel.core.serialize.SerializableEntity;

public class AbstractCachedMessage<T extends AbstractCachedMessage> implements SerializableEntity {
    protected Long cacheId;

    public T setCacheId(Long cacheId) {
        this.cacheId = cacheId;
        return (T) this;
    }

    public Long getCacheId() {
        return cacheId;
    }
}

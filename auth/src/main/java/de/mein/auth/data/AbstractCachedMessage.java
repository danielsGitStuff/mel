package de.mein.auth.data;

import de.mein.core.serialize.SerializableEntity;

public class AbstractCachedMessage<T extends AbstractCachedMessage> implements SerializableEntity {
    private Long cacheId;
    private String serviceUuid;

    public T setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        return (T) this;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

    public T setCacheId(Long cacheId) {
        this.cacheId = cacheId;
        return (T) this;
    }

    public Long getCacheId() {
        return cacheId;
    }
}

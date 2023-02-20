package de.mel.android;

import de.mel.core.serialize.SerializableEntity;

public class MelActivityPayload<T extends Object> implements SerializableEntity {
    private final String key;
    private final T payload;

    public MelActivityPayload(String key, T payload) {
        this.key = key;
        this.payload = payload;
    }

    public T getPayload() {
        return payload;
    }
}

package de.mel.android;

import de.mel.core.serialize.SerializableEntity;

public class MelActivityPayload<T extends Object> implements SerializableEntity {
    private String key;
    private T payload;

    public MelActivityPayload(String key, T payload) {
        this.key = key;
        this.payload = payload;
    }

    public MelActivityPayload(){
    }

    public T getPayload() {
        return payload;
    }
}

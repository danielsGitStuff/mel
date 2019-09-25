package de.mel.android;

public class MelActivityPayload<T extends Object> {
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

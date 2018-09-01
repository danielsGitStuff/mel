package de.mein.android;

public class MeinActivityPayload<T extends Object> {
    private final String key;
    private final T payload;

    public MeinActivityPayload(String key, T payload) {
        this.key = key;
        this.payload = payload;
    }

    public T getPayload() {
        return payload;
    }
}

package de.mein.core.serialize.exceptions;

/**
 * Created by xor on 28.10.2015.
 */
public class JsonDeserializationException extends Exception {
    public JsonDeserializationException(Exception e) {
        super(e);
    }

    public JsonDeserializationException(String message) {
        super(message);
    }
}

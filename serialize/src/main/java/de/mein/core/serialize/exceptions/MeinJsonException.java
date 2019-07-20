package de.mein.core.serialize.exceptions;

public class MeinJsonException extends Exception {
    public MeinJsonException(Exception e) {
        super(e);
    }

    public MeinJsonException(String message) {
        super(message);
    }
}

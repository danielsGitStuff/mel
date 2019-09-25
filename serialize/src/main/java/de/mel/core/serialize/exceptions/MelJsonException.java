package de.mel.core.serialize.exceptions;

public class MelJsonException extends Exception {
    public MelJsonException(Exception e) {
        super(e);
    }

    public MelJsonException(String message) {
        super(message);
    }
}

package de.mein.auth.data;

import de.mein.core.serialize.JsonIgnore;

/**
 * Created by xor on 5/2/16.
 */
public class ResponseException extends Exception implements IPayload {
    private String message;
    @JsonIgnore
    private Throwable cause;

    public ResponseException() {
        this.setStackTrace(new StackTraceElement[0]);
    }

    public ResponseException(Exception e) {
        this.message = e.toString() + "\n" + e.getMessage();
    }

    @Override
    public String getMessage() {
        return message;
    }
}

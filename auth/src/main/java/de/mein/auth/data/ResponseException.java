package de.mein.auth.data;

import de.mein.core.serialize.JsonIgnore;

/**
 * Created by xor on 5/2/16.
 */
public class ResponseException extends Exception implements ServicePayload {
    private String message;
    @JsonIgnore
    private Throwable cause;

    public ResponseException() {
        this.setStackTrace(new StackTraceElement[0]);
    }

    public ResponseException(String msg){
        message = msg;
    }

    public ResponseException(Exception e) {
        this.message = e.toString() + "\n" + e.getMessage();
    }

    @Override
    public String getMessage() {
        return message;
    }
}

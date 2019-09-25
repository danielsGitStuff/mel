package de.mel.auth.data;

import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;

/**
 * Created by xor on 5/2/16.
 */
public class ResponseException extends Exception implements SerializableEntity {
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

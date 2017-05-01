package de.mein.core.serialize.exceptions;

import org.json.JSONObject;

/**
 * You can return this one if you want to inform the client about what went
 * wrong. It also takes care about logging the error when calling the
 * constructor.
 *
 * @author xor
 *
 */
public abstract class AbstractException extends Exception {
    protected final Exception exception;

    public AbstractException(Exception e) {
        this.exception =e;
    }


    public String getMessage() {
        return exception.getMessage();
    }

    public Exception getException() {
        return exception;
    }

    public Throwable getCause() {
        return exception.getCause();
    }

    public StackTraceElement[] getStackTrace() {
        return exception.getStackTrace();
    }

    @Override
    public String toString() {
        JSONObject object = new JSONObject(this);
        return object.toString();
    }
}

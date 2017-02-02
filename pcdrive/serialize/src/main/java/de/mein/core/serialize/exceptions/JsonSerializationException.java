package de.mein.core.serialize.exceptions;

/**
 * You can return this one if you want to inform the client about what went
 * wrong. It also takes care about logging the error when calling the
 * constructor.
 *
 * @author DECK006
 *
 */
public class JsonSerializationException extends Exception {

    public JsonSerializationException(Exception e) {
        super(e);
    }
}

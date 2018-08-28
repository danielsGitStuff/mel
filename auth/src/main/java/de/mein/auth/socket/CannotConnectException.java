package de.mein.auth.socket;

/**
 * Created by xor on 10/25/16.
 */
public class CannotConnectException extends Exception {
    public CannotConnectException(Exception e, String address, int port) {
        super("Cannot connect to " + address + ":" + port + " cause of'" + e.toString() + "'");
    }
}

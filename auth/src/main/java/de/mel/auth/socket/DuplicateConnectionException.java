package de.mel.auth.socket;

/**
 * The connection lost against another connection to the same partner (see ConnectedEnvironment). That other one is the one to use.
 */
public class DuplicateConnectionException extends Exception {
    public DuplicateConnectionException() {
        super("lost against another connection to the same partner");
    }
}

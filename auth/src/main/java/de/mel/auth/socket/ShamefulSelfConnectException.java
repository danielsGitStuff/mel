package de.mel.auth.socket;

/**
 * Created by xor on 10/21/16.
 */
public class ShamefulSelfConnectException extends Exception {
    public ShamefulSelfConnectException() {
        super("I am a dumb computer. I've tried to connect to myself :(");
    }
}

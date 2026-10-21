package de.mel.auth.socket;

/**
 * We wanted to reach one partner and another trusted partner answered at that address.
 */
public class WrongPartnerException extends Exception {
    public WrongPartnerException(String message) {
        super(message);
    }
}

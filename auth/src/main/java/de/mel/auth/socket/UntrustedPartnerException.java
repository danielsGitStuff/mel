package de.mel.auth.socket;

/**
 * Whoever answered shows a certificate that is not a trusted partner.
 */
public class UntrustedPartnerException extends Exception {
    public UntrustedPartnerException(String message) {
        super(message);
    }
}

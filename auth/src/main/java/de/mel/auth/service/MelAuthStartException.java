package de.mel.auth.service;

/**
 * The instance could not start: a part of the auth worker (connections, certificate delivery, discovery) did not come up,
 * mostly because its port is taken. The message says which port; the cause is what the part ran into.
 * {@link MelBoot#boot()} rejects with this (or with what went wrong before the start).
 */
public class MelAuthStartException extends Exception {
    public MelAuthStartException(Exception cause) {
        super(cause == null ? "a part of the auth worker could not start" : cause.getMessage(), cause);
    }
}

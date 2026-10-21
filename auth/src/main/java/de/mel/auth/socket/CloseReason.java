package de.mel.auth.socket;

/**
 * Why a socket was closed. Every socket is closed exactly once and tells its listener the reason.
 * Whether the other side or we ended the connection is told separately ("remote").
 */
public enum CloseReason {
    /**
     * the partner hung up (end of stream), or the connection broke
     */
    PEER_CLOSED,
    /**
     * we shut down on purpose (service or instance goes down, someone stopped the socket)
     */
    SHUTDOWN,
    /**
     * the partner sent something that does not fit the state of the connection
     */
    PROTOCOL_VIOLATION,
    /**
     * there is another connection to the same partner and this one lost
     */
    DUPLICATE,
    /**
     * the partner shows a certificate we do not trust
     */
    UNTRUSTED,
    /**
     * the partner is a trusted one, but not the one we wanted to reach
     */
    WRONG_PARTNER,
    /**
     * the partner refused us
     */
    REFUSED,
    /**
     * the partner may not use the service it asked for
     */
    NO_ACCESS,
    /**
     * a registration was rejected (by a user)
     */
    REJECTED,
    /**
     * the registration is complete, the socket has done its job
     */
    REGISTERED,
    /**
     * the socket has done its job (e.g. certificate delivery), nothing went wrong
     */
    FINISHED,
    /**
     * the partner did not say or answer something in time
     */
    TIMEOUT,
    /**
     * reading or writing failed
     */
    IO_ERROR,
    /**
     * something went wrong on our side
     */
    INTERNAL_ERROR
}

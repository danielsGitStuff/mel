package de.mel.auth.data;

import de.mel.core.serialize.SerializableEntity;

/**
 * Sent right before a connection is closed because the other side broke the rules of the protocol, or for another reason that ends the connection.
 */
public class ProtocolError implements SerializableEntity {
    private String reason;
    private String description;

    public ProtocolError() {
    }

    public ProtocolError(String reason, String description) {
        this.reason = reason;
        this.description = description;
    }

    /**
     * name of a {@link de.mel.auth.socket.CloseReason}
     */
    public String getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }
}

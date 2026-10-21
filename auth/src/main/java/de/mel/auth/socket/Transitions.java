package de.mel.auth.socket;

import de.mel.auth.MelStrings;
import de.mel.auth.data.MelMessage;
import de.mel.auth.data.MelRegisterConfirm;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.MelResponse;
import de.mel.auth.data.ProtocolError;
import de.mel.core.serialize.SerializableEntity;

/**
 * The table of what may arrive in which state, without sockets and without I/O.
 * <pre>
 * state          | client                 | server
 * NEW            | nothing (it sends)     | register request, auth request
 * REGISTERING    | decision               | decision
 * AUTHENTICATING | response               | nothing (decides at once)
 * ESTABLISHED    | service traffic        | service traffic
 * ISOLATED       | blocks, isolate hello  | blocks, isolate hello
 * any            | error (closes)         | error (closes)
 * anything else is a protocol violation
 * </pre>
 */
public final class Transitions {
    private Transitions() {
    }

    public enum Role {CLIENT, SERVER}

    public enum MessageKind {
        REGISTER_REQUEST, REGISTER_DECISION, AUTH_REQUEST,
        /**
         * answers to requests, of the authentication and of services alike
         */
        RESPONSE,
        /**
         * everything else that is meant for a service
         */
        SERVICE,
        ISOLATE_HANDSHAKE, BLOCK, ERROR, UNPARSABLE
    }

    public enum Verdict {
        /**
         * the process of the current state handles it
         */
        DELIVER,
        /**
         * create the register process, switch to REGISTERING, let it handle the message
         */
        START_REGISTERING,
        START_AUTHENTICATING,
        /**
         * the partner ended the connection with an error message
         */
        CLOSE_BY_PARTNER_ERROR,
        /**
         * send an error, close with PROTOCOL_VIOLATION
         */
        VIOLATION,
        /**
         * the connection is closed already
         */
        IGNORE
    }

    public static MessageKind classify(SerializableEntity entity) {
        if (entity instanceof ProtocolError)
            return MessageKind.ERROR;
        if (entity instanceof MelRequest) {
            MelRequest request = (MelRequest) entity;
            if (MelStrings.SERVICE_NAME.equals(request.getServiceUuid())) {
                if (MelStrings.msg.INTENT_REGISTER.equals(request.getIntent()))
                    return MessageKind.REGISTER_REQUEST;
                if (MelStrings.msg.INTENT_AUTH.equals(request.getIntent()))
                    return MessageKind.AUTH_REQUEST;
            }
            return MessageKind.SERVICE;
        }
        if (entity instanceof MelResponse)
            return MessageKind.RESPONSE;
        if (entity instanceof MelMessage && ((MelMessage) entity).getPayload() instanceof MelRegisterConfirm)
            return MessageKind.REGISTER_DECISION;
        return MessageKind.SERVICE;
    }

    public static Verdict onMessage(SocketState.Kind state, Role role, MessageKind message) {
        if (state == SocketState.Kind.CLOSED)
            return Verdict.IGNORE;
        if (message == MessageKind.ERROR)
            return Verdict.CLOSE_BY_PARTNER_ERROR;
        switch (state) {
            case NEW:
                if (role == Role.SERVER) {
                    if (message == MessageKind.REGISTER_REQUEST)
                        return Verdict.START_REGISTERING;
                    if (message == MessageKind.AUTH_REQUEST)
                        return Verdict.START_AUTHENTICATING;
                }
                return Verdict.VIOLATION;
            case REGISTERING:
                return message == MessageKind.REGISTER_DECISION ? Verdict.DELIVER : Verdict.VIOLATION;
            case AUTHENTICATING:
                return role == Role.CLIENT && message == MessageKind.RESPONSE ? Verdict.DELIVER : Verdict.VIOLATION;
            case ESTABLISHED:
                return message == MessageKind.SERVICE || message == MessageKind.RESPONSE ? Verdict.DELIVER : Verdict.VIOLATION;
            case ISOLATED:
                return message == MessageKind.BLOCK || message == MessageKind.ISOLATE_HANDSHAKE ? Verdict.DELIVER : Verdict.VIOLATION;
            default:
                return Verdict.VIOLATION;
        }
    }
}

package de.mel.auth.socket;

import de.mel.auth.MelStrings;
import de.mel.auth.data.EmptyPayload;
import de.mel.auth.data.MelMessage;
import de.mel.auth.data.MelRegisterConfirm;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.MelResponse;
import de.mel.auth.data.ProtocolError;
import de.mel.auth.socket.SocketState.Kind;
import de.mel.auth.socket.Transitions.MessageKind;
import de.mel.auth.socket.Transitions.Role;
import de.mel.auth.socket.Transitions.Verdict;
import org.junit.jupiter.api.Test;

import static de.mel.auth.socket.Transitions.MessageKind.*;
import static de.mel.auth.socket.Transitions.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The table of SOCKET_PROCESS_REWORK.md, every state against every kind of message, without a socket.
 */
public class TransitionsTest {

    private static Verdict expected(Kind state, Role role, MessageKind message) {
        if (state == Kind.CLOSED)
            return IGNORE;
        if (message == ERROR)
            return CLOSE_BY_PARTNER_ERROR;
        switch (state) {
            case NEW:
                if (role == Role.SERVER && message == REGISTER_REQUEST)
                    return START_REGISTERING;
                if (role == Role.SERVER && message == AUTH_REQUEST)
                    return START_AUTHENTICATING;
                return VIOLATION;
            case REGISTERING:
                return message == REGISTER_DECISION ? DELIVER : VIOLATION;
            case AUTHENTICATING:
                return role == Role.CLIENT && message == RESPONSE ? DELIVER : VIOLATION;
            case ESTABLISHED:
                return message == SERVICE || message == RESPONSE ? DELIVER : VIOLATION;
            case ISOLATED:
                return message == BLOCK || message == ISOLATE_HANDSHAKE ? DELIVER : VIOLATION;
            default:
                throw new IllegalStateException();
        }
    }

    @Test
    public void everyStateAgainstEveryMessageKindAndRole() {
        int checked = 0;
        for (Kind state : Kind.values())
            for (Role role : Role.values())
                for (MessageKind message : MessageKind.values()) {
                    assertEquals(expected(state, role, message), Transitions.onMessage(state, role, message), state + "/" + role + "/" + message);
                    checked++;
                }
        assertEquals(6 * 2 * 9, checked);
    }

    @Test
    public void anythingUnparsableIsAViolationWhereverTheConnectionIsAlive() {
        for (Kind state : Kind.values())
            for (Role role : Role.values())
                assertEquals(state == Kind.CLOSED ? IGNORE : VIOLATION, Transitions.onMessage(state, role, UNPARSABLE));
    }

    @Test
    public void aBlockOnlyFitsAnIsolatedConnection() {
        for (Kind state : Kind.values())
            for (Role role : Role.values())
                if (state != Kind.ISOLATED && state != Kind.CLOSED)
                    assertEquals(VIOLATION, Transitions.onMessage(state, role, BLOCK), state + "/" + role);
    }

    @Test
    public void aRegistrationOrAuthOnAnEstablishedConnectionIsAViolation() {
        for (Role role : Role.values()) {
            assertEquals(VIOLATION, Transitions.onMessage(Kind.ESTABLISHED, role, REGISTER_REQUEST));
            assertEquals(VIOLATION, Transitions.onMessage(Kind.ESTABLISHED, role, AUTH_REQUEST));
            assertEquals(VIOLATION, Transitions.onMessage(Kind.ESTABLISHED, role, REGISTER_DECISION));
        }
    }

    @Test
    public void aClientDoesNotReceiveRequestsOnANewConnection() {
        assertEquals(VIOLATION, Transitions.onMessage(Kind.NEW, Role.CLIENT, AUTH_REQUEST));
        assertEquals(VIOLATION, Transitions.onMessage(Kind.NEW, Role.CLIENT, REGISTER_REQUEST));
    }

    @Test
    public void messagesAreClassifiedByWhatTheyAre() {
        assertEquals(REGISTER_REQUEST, Transitions.classify(new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_REGISTER)));
        assertEquals(AUTH_REQUEST, Transitions.classify(new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_AUTH)));
        assertEquals(SERVICE, Transitions.classify(new MelRequest("a service", MelStrings.msg.INTENT_AUTH)));
        assertEquals(SERVICE, Transitions.classify(new MelRequest(MelStrings.SERVICE_NAME, "something else")));
        assertEquals(RESPONSE, Transitions.classify(new MelResponse()));
        assertEquals(REGISTER_DECISION, Transitions.classify(new MelMessage(MelStrings.SERVICE_NAME, null).setPayLoad(new MelRegisterConfirm().setConfirmed(true))));
        assertEquals(SERVICE, Transitions.classify(new MelMessage("a service", null).setPayLoad(new EmptyPayload("x"))));
        assertEquals(ERROR, Transitions.classify(new ProtocolError("PROTOCOL_VIOLATION", "because")));
    }
}

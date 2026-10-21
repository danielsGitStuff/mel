package de.mel.auth.socket;

/**
 * What the worker of a socket handles, one after the other. The reader thread of the socket and everybody who wants the socket
 * closed put events into the same queue, so a close never overtakes a message that was received before it.
 */
abstract class SocketEvent {

    static final class MessageReceived extends SocketEvent {
        final String message;

        MessageReceived(String message) {
            this.message = message;
        }
    }

    static final class BlockReceived extends SocketEvent {
        final byte[] block;

        BlockReceived(byte[] block) {
            this.block = block;
        }
    }

    /**
     * the partner confirmed that the connection is isolated from now on
     */
    static final class IsolateConfirmed extends SocketEvent {
    }

    /**
     * end of stream or an I/O error in the reader
     */
    static final class PeerClosed extends SocketEvent {
        final Exception cause;

        PeerClosed(Exception cause) {
            this.cause = cause;
        }
    }

    static final class CloseRequested extends SocketEvent {
        final CloseReason reason;

        CloseRequested(CloseReason reason) {
            this.reason = reason;
        }
    }
}

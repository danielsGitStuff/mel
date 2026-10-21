package de.mel.auth.socket;

import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;

/**
 * Where a connection is in its life: <code>NEW -&gt; REGISTERING | AUTHENTICATING -&gt; ESTABLISHED | ISOLATED -&gt; CLOSED</code>.
 * States are immutable. They carry the process that handles the connection in that state and, from ESTABLISHED on, the partner,
 * so "established" implies "authenticated" by construction. Only the worker of the socket changes the state.
 */
public abstract class SocketState {
    public enum Kind {NEW, REGISTERING, AUTHENTICATING, ESTABLISHED, ISOLATED, CLOSED}

    public abstract Kind kind();

    /**
     * @return the process that handles messages in this state, null if there is none
     */
    public MelProcess process() {
        return null;
    }

    /**
     * @return the partner, only known from ESTABLISHED on
     */
    public Certificate partner() {
        return null;
    }

    @Override
    public String toString() {
        return kind().toString();
    }

    public static final class New extends SocketState {
        @Override
        public Kind kind() {
            return Kind.NEW;
        }
    }

    public static final class Registering extends SocketState {
        private final MelRegisterProcess process;

        public Registering(MelRegisterProcess process) {
            this.process = process;
        }

        @Override
        public Kind kind() {
            return Kind.REGISTERING;
        }

        @Override
        public MelRegisterProcess process() {
            return process;
        }
    }

    public static final class Authenticating extends SocketState {
        private final MelAuthProcess process;

        public Authenticating(MelAuthProcess process) {
            this.process = process;
        }

        @Override
        public Kind kind() {
            return Kind.AUTHENTICATING;
        }

        @Override
        public MelAuthProcess process() {
            return process;
        }
    }

    public static final class Established extends SocketState {
        private final MelValidationProcess process;
        private final Certificate partner;

        public Established(MelValidationProcess process, Certificate partner) {
            this.process = process;
            this.partner = partner;
        }

        @Override
        public Kind kind() {
            return Kind.ESTABLISHED;
        }

        @Override
        public MelValidationProcess process() {
            return process;
        }

        @Override
        public Certificate partner() {
            return partner;
        }
    }

    public static final class Isolated extends SocketState {
        private final MelIsolatedProcess process;
        private final Certificate partner;

        public Isolated(MelIsolatedProcess process, Certificate partner) {
            this.process = process;
            this.partner = partner;
        }

        @Override
        public Kind kind() {
            return Kind.ISOLATED;
        }

        @Override
        public MelIsolatedProcess process() {
            return process;
        }

        @Override
        public Certificate partner() {
            return partner;
        }
    }

    public static final class Closed extends SocketState {
        private final CloseReason reason;
        private final boolean remote;

        public Closed(CloseReason reason, boolean remote) {
            this.reason = reason;
            this.remote = remote;
        }

        @Override
        public Kind kind() {
            return Kind.CLOSED;
        }

        public CloseReason reason() {
            return reason;
        }

        /**
         * whether the partner ended the connection
         */
        public boolean remote() {
            return remote;
        }

        @Override
        public String toString() {
            return "CLOSED(" + reason + (remote ? ", by the partner" : "") + ")";
        }
    }
}

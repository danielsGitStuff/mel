package de.mel.auth.socket;

import de.mel.Lok;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A connection must not stay in NEW (the first message of a connection we accepted) or AUTHENTICATING forever:
 * if the socket is still in the given state when the time is up, it is closed with {@link CloseReason#TIMEOUT}.
 */
class StateTimeout {
    private final MelAuthSocket socket;
    private volatile ScheduledFuture<?> future;

    StateTimeout(MelAuthSocket socket) {
        this.socket = socket;
    }

    void start(SocketState.Kind kind) {
        cancel();
        try {
            future = socket.getMelAuthService().getTimeoutScheduler().schedule(() -> {
                if (socket.getState().kind() == kind) {
                    Lok.debug(socket.getMelAuthService().getName() + ".MelAuthSocket(v=" + socket.getV() + "): timeout in state " + kind);
                    socket.close(CloseReason.TIMEOUT);
                }
            }, socket.getMelAuthService().getStateTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // the instance is shut down
        }
    }

    void cancel() {
        ScheduledFuture<?> timeout = future;
        if (timeout != null)
            timeout.cancel(false);
    }
}

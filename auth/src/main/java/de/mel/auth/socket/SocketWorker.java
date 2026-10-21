package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.jobs.BlockReceivedJob;
import org.jdeferred.Promise;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The only consumer of the events of a {@link MelSocket}: it hands received messages to the listener one at a time and
 * closes the socket when a close event comes. Events before the close event are handled first.
 */
class SocketWorker extends DeferredRunnable {

    private final MelSocket socket;
    private static final AtomicInteger INDEX = new AtomicInteger(0);
    public final int index;

    SocketWorker(MelSocket socket) {
        this.socket = socket;
        this.index = INDEX.getAndIncrement();
    }

    @Override
    public void runImpl() {
        try {
            while (true) {
                SocketEvent event = socket.takeEvent();
                if (event instanceof SocketEvent.CloseRequested) {
                    socket.finishClose(((SocketEvent.CloseRequested) event).reason, ((SocketEvent.CloseRequested) event).remote);
                    return;
                }
                if (event instanceof SocketEvent.Violation) {
                    socket.finishClose(CloseReason.PROTOCOL_VIOLATION, false);
                    return;
                }
                if (event instanceof SocketEvent.PeerClosed) {
                    Exception cause = ((SocketEvent.PeerClosed) event).cause;
                    socket.finishClose(cause instanceof java.io.EOFException ? CloseReason.PEER_CLOSED : CloseReason.IO_ERROR, true);
                    return;
                }
                try {
                    handle(event);
                } catch (Throwable t) {
                    Lok.error("exception in " + getRunnableName() + ": " + t);
                    t.printStackTrace();
                } finally {
                    socket.eventHandled(event);
                }
            }
        } catch (InterruptedException e) {
            socket.finishClose(CloseReason.SHUTDOWN, false);
        }
    }

    private void handle(SocketEvent event) {
        MelSocket.MelSocketListener listener = socket.getListener();
        if (listener == null)
            return;
        if (event instanceof SocketEvent.MessageReceived) {
            listener.onMessage(socket, ((SocketEvent.MessageReceived) event).message);
        } else if (event instanceof SocketEvent.BlockReceived) {
            listener.onBlockReceived(new BlockReceivedJob().setBlock(((SocketEvent.BlockReceived) event).block));
        }
    }

    @Override
    public String getRunnableName() {
        return socket.getRunnableName() + "/WORK";
    }

    /**
     * The worker ends with the socket, there is nothing to shut down.
     */
    @Override
    public Promise<Void, Void, Void> onShutDown() {
        return DeferredRunnable.ResolvedDeferredObject();
    }
}

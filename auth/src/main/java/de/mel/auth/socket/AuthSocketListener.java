package de.mel.auth.socket;

import de.mel.auth.data.ProtocolError;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;

/**
 * What a {@link MelAuthSocket} does with what its worker hands over: ask {@link Transitions} what the message means in the
 * current state, then deliver it, switch the state, or close. Called by the worker of the socket only.
 */
class AuthSocketListener implements MelSocket.MelSocketListener {
    private final MelAuthSocket socket;

    AuthSocketListener(MelAuthSocket socket) {
        this.socket = socket;
    }

    @Override
    public void onMessage(MelSocket melSocket, String msg) {
        try {
            socket.getMelAuthService().getPowerManager().wakeLock(socket);
            SerializableEntity deserialized = null;
            Transitions.MessageKind kind;
            try {
                deserialized = SerializableEntityDeserializer.deserialize(msg);
                kind = deserialized == null ? Transitions.MessageKind.UNPARSABLE : Transitions.classify(deserialized);
            } catch (Exception e) {
                kind = Transitions.MessageKind.UNPARSABLE;
            }
            SocketState current = socket.getState();
            Transitions.Role role = socket.getRole();
            switch (Transitions.onMessage(current.kind(), role, kind)) {
                case DELIVER:
                    current.process().onMessageReceived(deserialized, socket);
                    break;
                case START_REGISTERING: {
                    MelRegisterProcess process = new MelRegisterProcess(socket);
                    socket.setState(new SocketState.Registering(process));
                    process.onMessageReceived(deserialized, socket);
                    break;
                }
                case START_AUTHENTICATING: {
                    MelAuthProcess process = new MelAuthProcess(socket);
                    socket.setState(new SocketState.Authenticating(process));
                    process.onMessageReceived(deserialized, socket);
                    break;
                }
                case CLOSE_BY_PARTNER_ERROR:
                    socket.close(reasonOf((ProtocolError) deserialized), true);
                    break;
                case VIOLATION:
                    socket.protocolViolation(kind + " does not fit state " + current.kind() + " (" + role + ")");
                    break;
                case IGNORE:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            socket.getMelAuthService().getPowerManager().releaseWakeLock(socket);
        }
    }

    @Override
    public void onBlockReceived(BlockReceivedJob block) {
        SocketState current = socket.getState();
        Transitions.Verdict verdict = Transitions.onMessage(current.kind(), socket.getRole(), Transitions.MessageKind.BLOCK);
        if (verdict == Transitions.Verdict.DELIVER)
            ((MelIsolatedProcess) current.process()).onBlockReceived(block);
        else if (verdict == Transitions.Verdict.VIOLATION)
            socket.protocolViolation("a block does not fit state " + current.kind() + " (" + socket.getRole() + ")");
    }

    @Override
    public void onClose(CloseReason reason, boolean remote) {
        MelProcess process = socket.getState().process();
        socket.cancelStateTimeout();
        // the state is left only after the process and the instance have seen the end: they ask the socket what it was
        try {
            if (process != null)
                process.onSocketClosed(reason, remote);
            socket.getMelAuthService().onSocketClosed(socket);
        } finally {
            socket.markClosed(new SocketState.Closed(reason, remote));
        }
    }

    private static CloseReason reasonOf(ProtocolError error) {
        try {
            return CloseReason.valueOf(error.getReason());
        } catch (Exception e) {
            return CloseReason.PROTOCOL_VIOLATION;
        }
    }

    @Override
    public void onOpen() {
    }

    @Override
    public void onError(Exception ex) {
    }
}

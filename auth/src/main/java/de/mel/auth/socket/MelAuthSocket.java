package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.ProtocolError;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.val.MelServicesPayload;
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.Hash;
import de.mel.sql.SqlQueriesException;

import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import javax.net.ssl.SSLSocket;

import java.io.IOException;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Created by xor on 10.08.2016.
 */
@SuppressWarnings("Duplicates")
public class MelAuthSocket extends MelSocket implements MelSocket.MelSocketListener {

    private final AtomicReference<SocketState> state = new AtomicReference<>(new SocketState.New());
    private final Transitions.Role role;
    protected Certificate partnerCertificate;
    private AConnectJob connectJob;

    /**
     * a socket that we will connect
     */
    public MelAuthSocket(MelAuthService melAuthService) {
        super(melAuthService, null);
        this.role = Transitions.Role.CLIENT;
        setListener(this);
    }

    /**
     * a socket that somebody connected to us
     */
    public MelAuthSocket(MelAuthService melAuthService, Socket socket) {
        super(melAuthService, socket);
        this.role = Transitions.Role.SERVER;
        setListener(this);
    }

    /**
     * a socket that we will connect
     */
    public MelAuthSocket(MelAuthService melAuthService, AConnectJob connectJob) {
        super(melAuthService, null);
        this.connectJob = connectJob;
        this.role = Transitions.Role.CLIENT;
        setListener(this);
    }


    public static String getAddressString(InetAddress address, int port) {
        return address.getHostAddress() + ":" + port;
    }


    public AConnectJob getConnectJob() {
        return connectJob;
    }

    public Certificate getPartnerCertificate() {
        return partnerCertificate;
    }

    public String getAddressString() {
        int port = N.result(() -> {
            if (connectJob == null)
                return socket.getLocalPort();
            return connectJob.getPort();
        });
        return MelAuthSocket.getAddressString(socket.getInetAddress(), port);
    }

    public SocketState getState() {
        return state.get();
    }

    /**
     * Only the worker of this socket calls this, or whoever sets the socket up before it is started.
     */
    void setState(SocketState next) {
        SocketState old = state.get();
        if (old.kind() == SocketState.Kind.CLOSED)
            return;
        Lok.debug(melAuthService.getName() + ".MelAuthSocket(v=" + getV() + "): " + old + " -> " + next);
        state.set(next);
        if (next.kind() == SocketState.Kind.AUTHENTICATING)
            startStateTimeout(next.kind());
        else
            cancelStateTimeout();
    }

    private volatile ScheduledFuture<?> stateTimeout;

    /**
     * A connection must not stay in NEW (the first message of a connection we accepted) or AUTHENTICATING forever.
     */
    private void startStateTimeout(SocketState.Kind kind) {
        cancelStateTimeout();
        try {
            stateTimeout = melAuthService.getTimeoutScheduler().schedule(() -> {
                if (state.get().kind() == kind) {
                    Lok.debug(melAuthService.getName() + ".MelAuthSocket(v=" + getV() + "): timeout in state " + kind);
                    close(CloseReason.TIMEOUT);
                }
            }, melAuthService.getStateTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // the instance is shut down
        }
    }

    private void cancelStateTimeout() {
        ScheduledFuture<?> timeout = stateTimeout;
        if (timeout != null)
            timeout.cancel(false);
    }

    @Override
    public void start() {
        if (role == Transitions.Role.SERVER && state.get().kind() == SocketState.Kind.NEW)
            startStateTimeout(SocketState.Kind.NEW);
        super.start();
    }

    private final CompletableFuture<SocketState.Closed> closedFuture = new CompletableFuture<>();

    /**
     * @return completes, with the reason, when the socket is closed
     */
    public CompletableFuture<SocketState.Closed> whenClosed() {
        return closedFuture;
    }

    public Transitions.Role getRole() {
        return role;
    }

    /**
     * This connection lost against another one to the same partner: tell it, then close.
     */
    public void duplicateLost() {
        N.s(() -> send(SerializableEntitySerializer.serialize(new ProtocolError(CloseReason.DUPLICATE.name(), "there is another connection to you that wins"))));
        close(CloseReason.DUPLICATE);
    }

    /**
     * The partner broke the rules: tell it why, then close.
     */
    void protocolViolation(String description) {
        Lok.error(melAuthService.getName() + ".MelAuthSocket(v=" + getV() + ") protocol violation in state " + state.get() + ": " + description);
        N.s(() -> send(SerializableEntitySerializer.serialize(new ProtocolError(CloseReason.PROTOCOL_VIOLATION.name(), description))));
        close(CloseReason.PROTOCOL_VIOLATION);
    }

    @Override
    public void onMessage(MelSocket melSocket, String msg) {
        try {
            melAuthService.getPowerManager().wakeLock(this);
            SerializableEntity deserialized = null;
            Transitions.MessageKind kind;
            try {
                deserialized = SerializableEntityDeserializer.deserialize(msg);
                kind = deserialized == null ? Transitions.MessageKind.UNPARSABLE : Transitions.classify(deserialized);
            } catch (Exception e) {
                kind = Transitions.MessageKind.UNPARSABLE;
            }
            SocketState current = state.get();
            Transitions.Verdict verdict = Transitions.onMessage(current.kind(), role, kind);
            switch (verdict) {
                case DELIVER:
                    current.process().onMessageReceived(deserialized, this);
                    break;
                case START_REGISTERING: {
                    MelRegisterProcess melRegisterProcess = new MelRegisterProcess(this);
                    setState(new SocketState.Registering(melRegisterProcess));
                    melRegisterProcess.onMessageReceived(deserialized, this);
                    break;
                }
                case START_AUTHENTICATING: {
                    MelAuthProcess melAuthProcess = new MelAuthProcess(this);
                    setState(new SocketState.Authenticating(melAuthProcess));
                    melAuthProcess.onMessageReceived(deserialized, this);
                    break;
                }
                case CLOSE_BY_PARTNER_ERROR:
                    close(reasonOf((ProtocolError) deserialized), true);
                    break;
                case VIOLATION:
                    protocolViolation(kind + " does not fit state " + current.kind() + " (" + role + ")");
                    break;
                case IGNORE:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            melAuthService.getPowerManager().releaseWakeLock(this);

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

    @Override
    public void onClose(CloseReason reason, boolean remote) {
        MelProcess process = state.get().process();
        cancelStateTimeout();
        // the state is left only after the process and the instance have seen the end: they ask the socket what it was
        try {
            if (process != null)
                process.onSocketClosed(reason, remote);
            melAuthService.onSocketClosed(this);
        } finally {
            SocketState.Closed closed = new SocketState.Closed(reason, remote);
            state.set(closed);
            closedFuture.complete(closed);
        }
    }

    @Override
    public void onBlockReceived(BlockReceivedJob block) {
        SocketState current = state.get();
        Transitions.Verdict verdict = Transitions.onMessage(current.kind(), role, Transitions.MessageKind.BLOCK);
        if (verdict == Transitions.Verdict.DELIVER)
            ((MelIsolatedProcess) current.process()).onBlockReceived(block);
        else if (verdict == Transitions.Verdict.VIOLATION)
            protocolViolation("a block does not fit state " + current.kind() + " (" + role + ")");
    }

    void connectSSL(Long certId, String address, int port) throws SqlQueriesException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        Socket socket = melAuthService.getCertificateManager().createSocket();
        Lok.debug("MelAuthSocket.connectSSL: " + address + ":" + port);
        int timeout = (int) melAuthService.getConnectTimeoutMs();
        try {
            socket.connect(new InetSocketAddress(address, port), timeout);
            // the handshake is part of connecting: a partner that accepts and then says nothing must not hold us up
            socket.setSoTimeout(timeout);
            ((SSLSocket) socket).startHandshake();
            socket.setSoTimeout(0);
        } catch (IOException e) {
            N.s(socket::close);
            throw e;
        }
        setSocket(socket);
        start();
    }

    /**
     * @return the certificate the other side showed in the TLS handshake
     */
    X509Certificate getPeerCertificate() throws IOException {
        SSLSocket sslSocket = (SSLSocket) socket;
        return (X509Certificate) sslSocket.getSession().getPeerCertificates()[0];
    }

    Certificate getTrustedPartnerCertificate() throws IOException, CertificateEncodingException, SqlQueriesException, ShamefulSelfConnectException {
        if (partnerCertificate == null) {
            SSLSocket sslSocket = (SSLSocket) socket;
            java.security.cert.Certificate cert = sslSocket.getSession().getPeerCertificates()[0];
            byte[] certBytes = cert.getEncoded();
            String hash = Hash.sha256(certBytes);
            partnerCertificate = melAuthService.getCertificateManager().getTrustedCertificateByHash(hash);
            if (partnerCertificate == null) {
                if (Arrays.equals(melAuthService.getCertificateManager().getPublicKey().getEncoded(), cert.getPublicKey().getEncoded())) {
                    throw new ShamefulSelfConnectException();
                }
            }
        }
        return partnerCertificate;
    }

    public boolean isValidated() {
        return state.get().kind() == SocketState.Kind.ESTABLISHED;
    }

    /**
     * @return the process of the current state, null while there is none
     */
    public MelProcess getProcess() {
        return state.get().process();
    }

    @Override
    public Promise<Void, Void> onShutDown() {
        super.onShutDown();
        return null;
    }


    @Override
    public void stop() {
        super.stop();
    }

    public Socket getSocket() {
        //todo make package private
        return socket;
    }
}

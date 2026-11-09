package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.data.ProtocolError;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.Hash;
import de.mel.sql.SqlQueriesException;


import javax.net.ssl.SSLSocket;

import java.io.IOException;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Created by xor on 10.08.2016.
 */
public class MelAuthSocket extends MelSocket {

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
        setListener(new AuthSocketListener(this));
    }

    /**
     * a socket that somebody connected to us
     */
    public MelAuthSocket(MelAuthService melAuthService, Socket socket) {
        super(melAuthService, socket);
        this.role = Transitions.Role.SERVER;
        setListener(new AuthSocketListener(this));
    }

    /**
     * a socket that we will connect
     */
    public MelAuthSocket(MelAuthService melAuthService, AConnectJob connectJob) {
        super(melAuthService, null);
        this.connectJob = connectJob;
        this.role = Transitions.Role.CLIENT;
        setListener(new AuthSocketListener(this));
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
            stateTimeout.start(next.kind());
        else
            cancelStateTimeout();
    }

    private final StateTimeout stateTimeout = new StateTimeout(this);

    void cancelStateTimeout() {
        stateTimeout.cancel();
    }

    @Override
    public void start() {
        if (role == Transitions.Role.SERVER && state.get().kind() == SocketState.Kind.NEW)
            stateTimeout.start(SocketState.Kind.NEW);
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

    void markClosed(SocketState.Closed closed) {
        state.set(closed);
        closedFuture.complete(closed);
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

    public Socket getSocket() {
        return socket;
    }
}

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
public class MelAuthSocket {

    /**
     * Reads and writes frames and closes exactly once. This class decides what the frames mean.
     */
    private final MelSocket transport;

    private final AtomicReference<SocketState> state = new AtomicReference<>(new SocketState.New());
    private final Transitions.Role role;
    protected Certificate partnerCertificate;
    private AConnectJob connectJob;

    /**
     * a socket that we will connect
     */
    public MelAuthSocket(MelAuthService melAuthService) {
        this(melAuthService, new MelSocket(melAuthService), null, Transitions.Role.CLIENT);
    }

    /**
     * a socket that somebody connected to us
     */
    public MelAuthSocket(MelAuthService melAuthService, Socket socket) {
        this(melAuthService, new MelSocket(melAuthService, socket), null, Transitions.Role.SERVER);
    }

    /**
     * a socket that we will connect
     */
    public MelAuthSocket(MelAuthService melAuthService, AConnectJob connectJob) {
        this(melAuthService, new MelSocket(melAuthService), connectJob, Transitions.Role.CLIENT);
    }

    private MelAuthSocket(MelAuthService melAuthService, MelSocket transport, AConnectJob connectJob, Transitions.Role role) {
        this.transport = transport;
        this.connectJob = connectJob;
        this.role = role;
        transport.setOwner(this);
        transport.setListener(new AuthSocketListener(this));
    }

    public MelAuthService getMelAuthService() {
        return transport.getMelAuthService();
    }

    public int getV() {
        return transport.getV();
    }

    public void send(String json) {
        transport.send(json);
    }

    public void sendBlock(byte[] block) {
        transport.sendBlock(block);
    }

    public boolean isOpen() {
        return transport.isOpen();
    }

    public String getAddress() {
        return transport.getAddress();
    }

    public void setRunnableName(String name) {
        transport.setRunnableName(name);
    }

    public boolean isStopped() {
        return transport.isStopped();
    }

    public Socket getSocket() {
        return transport.socket;
    }

    /**
     * Closes after everything received before is handled, see {@link MelSocket#close(CloseReason)}.
     */
    public void close(CloseReason reason) {
        transport.close(reason);
    }

    public void close(CloseReason reason, boolean remote) {
        transport.close(reason, remote);
    }

    public CloseReason getCloseReason() {
        return transport.getCloseReason();
    }

    public boolean isClosedByPeer() {
        return transport.isClosedByPeer();
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
                return getSocket().getLocalPort();
            return connectJob.getPort();
        });
        return MelAuthSocket.getAddressString(getSocket().getInetAddress(), port);
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
        Lok.debug(getMelAuthService().getName() + ".MelAuthSocket(v=" + getV() + "): " + old + " -> " + next);
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

    public void start() {
        if (role == Transitions.Role.SERVER && state.get().kind() == SocketState.Kind.NEW)
            stateTimeout.start(SocketState.Kind.NEW);
        transport.start();
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
        Lok.error(getMelAuthService().getName() + ".MelAuthSocket(v=" + getV() + ") protocol violation in state " + state.get() + ": " + description);
        N.s(() -> send(SerializableEntitySerializer.serialize(new ProtocolError(CloseReason.PROTOCOL_VIOLATION.name(), description))));
        close(CloseReason.PROTOCOL_VIOLATION);
    }

    void markClosed(SocketState.Closed closed) {
        state.set(closed);
        closedFuture.complete(closed);
    }

    void connectSSL(Long certId, String address, int port) throws SqlQueriesException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        transport.setSocket(TlsConnect.connect(getMelAuthService(), address, port));
        start();
    }

    /**
     * @return the certificate the other side showed in the TLS handshake
     */
    X509Certificate getPeerCertificate() throws IOException {
        SSLSocket sslSocket = (SSLSocket) getSocket();
        return (X509Certificate) sslSocket.getSession().getPeerCertificates()[0];
    }

    Certificate getTrustedPartnerCertificate() throws IOException, CertificateEncodingException, SqlQueriesException, ShamefulSelfConnectException {
        if (partnerCertificate == null) {
            SSLSocket sslSocket = (SSLSocket) getSocket();
            java.security.cert.Certificate cert = sslSocket.getSession().getPeerCertificates()[0];
            byte[] certBytes = cert.getEncoded();
            String hash = Hash.sha256(certBytes);
            partnerCertificate = getMelAuthService().getCertificateManager().getTrustedCertificateByHash(hash);
            if (partnerCertificate == null) {
                if (Arrays.equals(getMelAuthService().getCertificateManager().getPublicKey().getEncoded(), cert.getPublicKey().getEncoded())) {
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

}

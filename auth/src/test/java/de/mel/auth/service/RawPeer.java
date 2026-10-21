package de.mel.auth.service;

import de.mel.auth.MelStrings;
import de.mel.auth.data.IsolationDetails;
import de.mel.auth.data.MelMessage;
import de.mel.auth.data.MelRegisterConfirm;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.FrameCodec;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * A hand-made TLS peer that talks to a Mel instance directly, for tests that need to send what a correct instance never would.
 * Everything that knows how bytes travel over a Mel socket and what the protocol messages look like is in here:
 * the socket rework changes this class, not the tests that use it.
 */
public class RawPeer implements AutoCloseable {
    private final Socket plain;
    private final SSLSocket socket;
    private final DataOutputStream out;
    private final DataInputStream in;
    private volatile Thread blockWriter;

    private RawPeer(Socket plain, SSLSocket socket) throws IOException {
        this.plain = plain;
        this.socket = socket;
        this.out = new DataOutputStream(socket.getOutputStream());
        this.in = new DataInputStream(socket.getInputStream());
    }

    /**
     * @param presentedCert the certificate shown during the TLS handshake, null = none at all
     * @param signingKey    the key that signs the handshake. Whoever shows a certificate without owning its key fails right there.
     */
    public static RawPeer connect(int port, X509Certificate presentedCert, PrivateKey signingKey) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        KeyManager[] keyManagers = null;
        if (presentedCert != null) {
            keyManagers = new KeyManager[]{new X509KeyManager() {
                public String[] getClientAliases(String keyType, Principal[] issuers) {
                    return new String[]{"peer"};
                }

                public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                    return "peer";
                }

                public String[] getServerAliases(String keyType, Principal[] issuers) {
                    return null;
                }

                public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                    return null;
                }

                public X509Certificate[] getCertificateChain(String alias) {
                    return new X509Certificate[]{presentedCert};
                }

                public PrivateKey getPrivateKey(String alias) {
                    return signingKey;
                }
            }};
        }
        // this peer does not care who the instance is
        context.init(keyManagers, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());
        // TLS on top of a socket of our own: close() can pull that one away under a write that blocks
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress("localhost", port), 5000);
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(plain, "localhost", port, true);
        return new RawPeer(plain, socket);
    }

    // ---- what the protocol messages look like ----

    /**
     * @param isolation null for a normal connection
     */
    public static MelRequest authRequest(IsolationDetails isolation) {
        MelRequest request = new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_AUTH);
        if (isolation != null)
            request.setPayLoad(isolation);
        return request;
    }

    public static MelRequest registerRequest(String name, X509Certificate certificate, int port, int deliveryPort) throws Exception {
        Certificate details = new Certificate()
                .setName(name)
                .setCertificate(certificate.getEncoded())
                .setPort(port)
                .setCertDeliveryPort(deliveryPort);
        return new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_REGISTER).setCertificate(details);
    }

    /**
     * the decision of this peer's user in a registration
     */
    public static MelMessage registerDecision(boolean accepted) {
        return new MelMessage(MelStrings.SERVICE_NAME, null).setPayLoad(new MelRegisterConfirm().setConfirmed(accepted));
    }

    // ---- how bytes travel ----

    public void send(SerializableEntity entity) throws Exception {
        sendText(SerializableEntitySerializer.serialize(entity));
    }

    /**
     * something that travels like a message but is none
     */
    public void sendUnparsable() throws IOException {
        sendText("this is not a message");
    }

    /**
     * A block of an isolated connection, whatever the state of this one is.
     * Written in the background: an instance that stops reading but keeps the connection open would block the test forever.
     */
    public void sendBlock() {
        byte[] block = new byte[64 * 1024];
        block[0] = 'T';
        Thread writer = new Thread(() -> {
            try {
                synchronized (out) {
                    FrameCodec.write(out, FrameCodec.BLOCK, block);
                }
            } catch (IOException e) {
                // hung up while we were writing, or closed by the test
            }
        }, "RawPeer.sendBlock");
        writer.setDaemon(true);
        blockWriter = writer;
        writer.start();
    }

    private void sendText(String text) throws IOException {
        synchronized (out) {
            FrameCodec.write(out, FrameCodec.MESSAGE, text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Only the header of a frame, as an instance that announces more than it sends or more than allowed.
     */
    public void sendRawHeader(byte type, int length) throws IOException {
        synchronized (out) {
            out.writeByte(type);
            out.writeInt(length);
            out.flush();
        }
    }

    /**
     * @return the next message, or null if the instance hung up, stayed silent for timeoutMillis or the TLS handshake failed
     */
    public SerializableEntity receive(int timeoutMillis) throws Exception {
        try {
            socket.setSoTimeout(timeoutMillis);
            FrameCodec.Frame frame = FrameCodec.read(in);
            return SerializableEntityDeserializer.deserialize(new String(frame.payload, StandardCharsets.UTF_8));
        } catch (EOFException | SocketTimeoutException | SSLException | SocketException e) {
            return null;
        }
    }

    /**
     * Reads and throws away whatever comes until the instance hangs up.
     *
     * @return whether it hung up within timeoutMillis
     */
    public boolean hangsUpWithin(int timeoutMillis) throws IOException {
        long end = System.currentTimeMillis() + timeoutMillis;
        byte[] buffer = new byte[8192];
        try {
            while (true) {
                long left = end - System.currentTimeMillis();
                if (left <= 0)
                    return false;
                socket.setSoTimeout((int) left);
                if (in.read(buffer) < 0)
                    return true;
            }
        } catch (SocketTimeoutException e) {
            return false;
        } catch (SSLException | SocketException e) {
            // reset or TLS closed without a proper goodbye: hung up all the same
            return true;
        }
    }

    @Override
    public void close() throws IOException {
        Thread writer = blockWriter;
        if (writer != null && writer.isAlive()) {
            // a TLS close would wait for the write lock that the stuck block holds
            plain.close();
            return;
        }
        socket.close();
    }
}

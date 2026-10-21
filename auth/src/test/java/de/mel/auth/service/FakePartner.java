package de.mel.auth.service;

import de.mel.auth.data.MelRequest;
import de.mel.auth.data.MelResponse;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.auth.socket.FrameCodec;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Listens on a port and shows a certificate of its choice, as whoever is at an address where a partner is expected.
 * It answers every authentication request with "ok", to show that the instance that connects does not just believe that.
 */
class FakePartner implements AutoCloseable {
    private final SSLServerSocket serverSocket;
    private final Thread thread;
    /**
     * completes when the instance hung up (or never connected: it does not complete then)
     */
    final CompletableFuture<Void> hungUp = new CompletableFuture<>();
    final CompletableFuture<MelRequest> authRequest = new CompletableFuture<>();

    FakePartner(int port, X509Certificate shown, PrivateKey key) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[]{new X509KeyManager() {
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return null;
            }

            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return null;
            }

            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return new String[]{"fake"};
            }

            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return "fake";
            }

            public X509Certificate[] getCertificateChain(String alias) {
                return new X509Certificate[]{shown};
            }

            public PrivateKey getPrivateKey(String alias) {
                return key;
            }
        }}, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.setWantClientAuth(true);
        serverSocket.bind(new InetSocketAddress(port));
        thread = new Thread(this::serve, "FakePartner");
        thread.setDaemon(true);
        thread.start();
    }

    private void serve() {
        try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            while (true) {
                FrameCodec.Frame frame = FrameCodec.read(in);
                SerializableEntity entity = SerializableEntityDeserializer.deserialize(new String(frame.payload, StandardCharsets.UTF_8));
                if (entity instanceof MelRequest request) {
                    authRequest.complete(request);
                    MelResponse response = request.reponse();
                    FrameCodec.write(out, FrameCodec.MESSAGE, SerializableEntitySerializer.serialize(response).getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            hungUp.complete(null);
        }
    }

    boolean sawHangUpWithin(int seconds) throws Exception {
        try {
            hungUp.get(seconds, TimeUnit.SECONDS);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        serverSocket.close();
    }
}

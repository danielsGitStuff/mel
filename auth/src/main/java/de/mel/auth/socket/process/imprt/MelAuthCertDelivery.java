package de.mel.auth.socket.process.imprt;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.MelResponse;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.socket.CloseReason;
import de.mel.auth.socket.FrameCodec;
import de.mel.auth.socket.MelSocket;
import de.mel.auth.tools.N;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import javax.net.ServerSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.X509Certificate;


/**
 * delivers certificates
 * Created by xor on 2/14/16.
 */
public class MelAuthCertDelivery extends DeferredRunnable {
    protected ServerSocket serverSocket;
    private final CertificateManager certificateManager;
    protected DataOutputStream out;
    protected DataInputStream in;
    private MelAuthServiceImpl melAuthService;
    private ServerSocketFactory serverSocketFactory;
    protected Integer port;
    protected MelSocket.MelSocketListener listener;


    public MelAuthCertDelivery(MelAuthServiceImpl melAuthService, int port) {
        this.melAuthService = melAuthService;
        this.certificateManager = melAuthService.getCertificateManager();
        this.port = port;
        this.serverSocketFactory = ServerSocketFactory.getDefault();

        this.listener = new MelSocket.MelSocketListener() {

            @Override
            public void onMessage(MelSocket melSocket, String messageString) {
                try {
                    Lok.debug(melAuthService.getName() + ".MelAuthCertDelivery.onMessage.got: " + messageString);
                    MelRequest request = (MelRequest) new SerializableEntityDeserializer().deserialize(messageString);
                    if (request.getServiceUuid().equals(MelStrings.SERVICE_NAME) && request.getIntent().equals(MelStrings.msg.GET_CERT)) {
                        X509Certificate x509Certificate = certificateManager.getMyX509Certificate();
                        Certificate certificate = new Certificate();
                        certificate.setCertificate(x509Certificate.getEncoded());
                        certificate.setName(melAuthService.getName());
                        certificate.setPort(melAuthService.getSettings().getPort());
                        certificate.setCertDeliveryPort(melAuthService.getSettings().getDeliveryPort());
                        MelResponse answer = createCertDeliveryAnswer(request, certificate);
                        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
                        serializer.setEntity(answer);
                        String json = serializer.JSON();
                        melSocket.send(json);
                    } else
                        melSocket.send("could not understand you");
                } catch (Exception e) {
                    melSocket.send("Errör");
                    e.printStackTrace();
                }
            }

            @Override
            public void onOpen() {
                Lok.debug("MelAuthCertDelivery.onOpen");
            }

            @Override
            public void onError(Exception ex) {
                Lok.debug("MelAuthCertDelivery.onError");
            }

            @Override
            public void onClose(CloseReason reason, boolean remote) {
                Lok.debug("MelAuthCertDelivery.onClose");
            }

            @Override
            public void onBlockReceived(BlockReceivedJob block) {

            }


        };
    }

    static MelResponse createCertDeliveryAnswer(MelRequest request, Certificate certificate) {
        return new MelResponse().setCertificate(certificate).setResponseId(request.getRequestId());
    }

    public static MelRequest createCertDeliveryGet() {
        return new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.GET_CERT);
    }


    @Override
    public Promise<Void, Void> onShutDown() {
        // with runImpl's bind: either the port was bound and is closed here, or it is never bound
        synchronized (this) {
            N.r(() -> {
                Lok.debug("unbinding " + port);
                serverSocket.close();
            });
        }
        return DeferredRunnable.ResolvedDeferredObject();
    }

    @Override
    public void runImpl() {
        try {
            synchronized (this) {
                if (isStopped())
                    return;
                serverSocket = serverSocketFactory.createServerSocket();
                serverSocket.setReuseAddress(true);
                Lok.debug("binding " + port);
                serverSocket.bind(new InetSocketAddress(port));
            }
            resolveStarted();
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                // every client has its own thread: one that says nothing must not hold up the others
                Thread client = new Thread(() -> serve(socket), "MelAuthCertDelivery client of " + melAuthService.getName());
                client.setDaemon(true);
                client.start();
            }

        } catch (Exception e) {
            if (!isStopped()) {
                e.printStackTrace();
                // a bind that failed is the end of the start (after the start this does nothing)
                rejectStarted(new IOException("port " + port + " (certificate delivery): " + e.getMessage(), e));
            } else
                Lok.debug("MelAuthCertDelivery.runImpl.interrupted");
        } finally {
            try {
                N.s(() -> serverSocket.close());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * Reads the request of one client, answers, hangs up. A client that does not send its request in time is dropped.
     */
    private void serve(Socket socket) {
        MelSocket melSocket = null;
        try {
            socket.setSoTimeout((int) melAuthService.getStateTimeoutMs());
            DataInputStream clientIn = new DataInputStream(socket.getInputStream());
            String s = new String(FrameCodec.read(clientIn).payload, java.nio.charset.StandardCharsets.UTF_8);
            Lok.debug(melAuthService.getName() + ".MelAuthCertDelivery.runTry.got: " + s);
            melSocket = new MelSocket(melAuthService, socket);
            this.listener.onMessage(melSocket, s);
            melSocket.close(CloseReason.FINISHED);
        } catch (Exception e) {
            Lok.debug("MelAuthCertDelivery: dropping a client: " + e);
            if (melSocket != null)
                melSocket.close(CloseReason.IO_ERROR);
            else
                N.s(socket::close);
        }
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + melAuthService.getName();
    }

}

package de.mein.auth.socket.process.imprt;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.MeinResponse;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.MeinSocket;
import de.mein.auth.tools.N;
import de.mein.auth.tools.lock.DEV_BIND;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import org.jdeferred.impl.DeferredObject;

import javax.net.ServerSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.Map;


/**
 * delivers certificates
 * Created by xor on 2/14/16.
 */
public class MeinAuthCertDelivery extends DeferredRunnable {
    protected ServerSocket serverSocket;
    private final CertificateManager certificateManager;
    protected DataOutputStream out;
    protected DataInputStream in;
    private MeinAuthService meinAuthService;
    private ServerSocketFactory serverSocketFactory;
    protected Integer port;
    protected MeinSocket.MeinSocketListener listener;


    public MeinAuthCertDelivery(MeinAuthService meinAuthService, int port) {
        this.meinAuthService = meinAuthService;
        this.certificateManager = meinAuthService.getCertificateManager();
        this.port = port;
        this.serverSocketFactory = ServerSocketFactory.getDefault();

        this.listener = new MeinSocket.MeinSocketListener() {
            @Override
            public void onIsolated() {

            }

            @Override
            public void onMessage(MeinSocket meinSocket, String messageString) {
                try {
                    Lok.debug(meinAuthService.getName() + ".MeinAuthCertDelivery.onMessage.got: " + messageString);
                    MeinRequest request = (MeinRequest) new SerializableEntityDeserializer().deserialize(messageString);
                    if (request.getServiceUuid().equals(MeinStrings.SERVICE_NAME) && request.getIntent().equals(MeinStrings.msg.GET_CERT)) {
                        X509Certificate x509Certificate = certificateManager.getMyX509Certificate();
                        Certificate certificate = new Certificate();
                        certificate.setCertificate(x509Certificate.getEncoded());
                        certificate.setName(meinAuthService.getName());
                        certificate.setPort(meinAuthService.getSettings().getPort());
                        certificate.setCertDeliveryPort(meinAuthService.getSettings().getDeliveryPort());
                        MeinResponse answer = createCertDeliveryAnswer(request, certificate);
                        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
                        serializer.setEntity(answer);
                        String json = serializer.JSON();
                        meinSocket.send(json);
                    } else
                        meinSocket.send("could not understand you");
                } catch (Exception e) {
                    meinSocket.send("Errör");
                    e.printStackTrace();
                }
            }

            @Override
            public void onOpen() {
                Lok.debug("MeinAuthCertDelivery.onOpen");
            }

            @Override
            public void onError(Exception ex) {
                Lok.debug("MeinAuthCertDelivery.onError");
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Lok.debug("MeinAuthCertDelivery.onClose");
            }

            @Override
            public void onBlockReceived(BlockReceivedJob block) {

            }


        };
    }

    static MeinResponse createCertDeliveryAnswer(MeinRequest request, Certificate certificate) {
        return new MeinResponse().setCertificate(certificate).setResponseId(request.getRequestId());
    }

    public static MeinRequest createCertDeliveryGet() {
        return new MeinRequest(MeinStrings.SERVICE_NAME, MeinStrings.msg.GET_CERT);
    }


    @Override
    public DeferredObject<Void, Void, Void> onShutDown() {
        //todo debug
        N.s(() -> in.close());
        N.s(() -> out.close());
        N.r(() -> {
            Lok.debug("unbinding " + port);
            serverSocket.close();
            DEV_BIND.serverSockets.remove(serverSocket);
        });
//        N.r(() -> serverSocket.close());
        return DeferredRunnable.ResolvedDeferredObject();
    }

    @Override
    public void runImpl() {
        try {
            serverSocket = serverSocketFactory.createServerSocket();
            serverSocket.setReuseAddress(true);
            Lok.debug("binding " + port);
            serverSocket.bind(new InetSocketAddress(port));
            DEV_BIND.add(serverSocket);
            startedPromise.resolve(null);
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                DEV_BIND.addSocket(socket);
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                String s = in.readUTF();
                Lok.debug(meinAuthService.getName() + ".MeinAuthCertDelivery.runTry.got: " + s);
                MeinSocket meinSocket = new MeinSocket(meinAuthService, socket);
                this.listener.onMessage(meinSocket, s);
                meinSocket.shutDown();
                /*for (MeinSocketListener listener : listeners) {
                    listener.onMessage(this, s);
                }*/
            }
            listener.onClose(42, "don't know shit...", true);

        } catch (Exception e) {
            if (!isStopped()) {
                e.printStackTrace();
                Map<ServerSocket, Integer> list = DEV_BIND.serverSockets;
                Map<Socket, Integer> sockets = DEV_BIND.sockets;
                Lok.debug();
                list.forEach((serverSocket1, integer) -> Lok.debug(serverSocket1));
            } else
                Lok.debug("MeinAuthCertDelivery.runImpl.interrupted");
        } finally {
            try {
                N.s(() -> serverSocket.close(), () -> in.close(), () -> out.close());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName();
    }

}

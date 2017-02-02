package de.mein.auth.socket.process.imprt;

import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.MeinResponse;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.MeinAuthServer;
import de.mein.auth.socket.MeinSocket;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import javax.net.ServerSocketFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 2/14/16.
 */
public class MeinAuthCertDelivery extends MeinAuthServer {
    private static Logger logger = Logger.getLogger(MeinAuthCertDelivery.class.getName());
    public static final String GET_CERT_ANSWER = "getCert.answer";
    public static final String GET_CERT = "getCert";
    private final CertificateManager certificateManager;
    protected DataOutputStream out;
    protected DataInputStream in;
    private MeinAuthService meinAuthService;


    public MeinAuthCertDelivery(MeinAuthService meinAuthService, int port) throws Exception {
        this.meinAuthService = meinAuthService;
        this.certificateManager = meinAuthService.getCertificateManager();
        this.port = port;
        this.setServerSocketFactory(ServerSocketFactory.getDefault());
        this.listener = new MeinSocket.MeinSocketListener() {
            @Override
            public void onIsolated() {

            }

            @Override
            public void onMessage(MeinSocket meinSocket, String messageString) {
                try {
                    logger.log(Level.FINEST, meinAuthService.getName() + ".MeinAuthCertDelivery.onMessage.got: " + messageString);
                    MeinRequest request = (MeinRequest) new SerializableEntityDeserializer().deserialize(messageString);
                    if (request.getServiceUuid().equals(MeinAuthService.SERVICE_NAME) && request.getIntent().equals(GET_CERT)) {
                        X509Certificate x509Certificate = certificateManager.getMyX509Certificate();
                        Certificate certificate = new Certificate();
                        certificate.setCertificate(x509Certificate.getEncoded());
                        certificate.setName(meinAuthService.getName());
                        certificate.setGreeting(meinAuthService.getSettings().getGreeting());
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
                logger.log(Level.FINEST, "MeinAuthCertDelivery.onOpen");
            }

            @Override
            public void onError(Exception ex) {
                logger.log(Level.FINEST, "MeinAuthCertDelivery.onError");
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.log(Level.FINEST, "MeinAuthCertDelivery.onClose");
            }

            @Override
            public void onBlockReceived(byte[] block) {

            }


        };
    }

    static MeinResponse createCertDeliveryAnswer(MeinRequest request, Certificate certificate) {
        return new MeinResponse().setCertificate(certificate).setResponseId(request.getRequestId());
    }

    public static MeinRequest createCertDeliveryGet() {
        return new MeinRequest(MeinAuthService.SERVICE_NAME, GET_CERT);
    }


    @Override
    public void run() {
        try {
            startedPromise.resolve(null);
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                String s = in.readUTF();
                logger.log(Level.FINEST, meinAuthService.getName() + ".MeinAuthCertDelivery.runTry.got: " + s);
                this.listener.onMessage(new MeinSocket(meinAuthService, socket), s);
                /*for (MeinSocketListener listener : listeners) {
                    listener.onMessage(this, s);
                }*/
            }
            listener.onClose(42, "don't know shit...", true);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
                out.close();
                serverSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

}

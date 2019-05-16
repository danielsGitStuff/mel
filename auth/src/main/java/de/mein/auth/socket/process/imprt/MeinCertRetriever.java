package de.mein.auth.socket.process.imprt;

import de.mein.DeferredRunnable;
import de.mein.auth.data.MeinMessage;
import de.mein.auth.data.MeinResponse;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.MeinSocket;
import de.mein.auth.socket.ShamefulSelfConnectException;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.impl.DeferredObject;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * transfers certificates and imports them into the database
 * Created by xor on 4/18/16.
 */
public class MeinCertRetriever extends DeferredRunnable {
    private static Logger logger = Logger.getLogger(MeinCertRetriever.class.getName());
    private final MeinAuthService meinAuthService;
    private final CertificateManager certificateManager;
    private Map<MeinSocket, Object> clientSockets = new ConcurrentHashMap<>();
    private DeferredObject<Certificate, Exception, Object> deferred;
    private int portCert;
    private String address;
    private int port;
    private MeinSocket certDeliveryClient;

    public MeinCertRetriever(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        this.certificateManager = meinAuthService.getCertificateManager();
    }

    public DeferredObject<Certificate, Exception, Object> retrieveCertificate(DeferredObject<Certificate, Exception, Object> deferred, String address, int port, int portCert) throws URISyntaxException, InterruptedException {
        this.address = address;
        this.port = port;
        this.portCert = portCert;
        this.deferred = deferred;
        meinAuthService.execute(this);
        return deferred;
    }


    public void stop() {

    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    @Override
    public void onShutDown() {
        certDeliveryClient.shutDown();
        for (MeinSocket socket : clientSockets.keySet()) {
            socket.shutDown();
        }
    }

    @Override
    public void runImpl() {
        try {
            // RWLock lock = new RWLock();
            //lock.lockWrite();
            logger.log(Level.FINER, "MeinCertRetriever.retrieveCertificate.ALPHONSO");
            URI uri = new URI(address);

            Socket socket = new Socket();
            certDeliveryClient = new MeinSocket(meinAuthService, socket);
            certDeliveryClient.setListener(new MeinSocket.MeinSocketListener() {
                @Override
                public void onIsolated() {

                }

                @Override
                public void onMessage(MeinSocket meinSocket, String messageString) {
                    logger.log(Level.FINEST, meinAuthService.getName() + ".MeinCertRetriever.onMessage.got: " + messageString);
                    try {
                        SerializableEntityDeserializer deserializer = new SerializableEntityDeserializer();
                        MeinResponse response = (MeinResponse) SerializableEntityDeserializer.deserialize(messageString);
                        Certificate certificate = response.getCertificate();
                        X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(certificate.getCertificate().v());
                        boolean isItMe = Arrays.equals(certificateManager.getMyX509Certificate().getPublicKey().getEncoded(), x509Certificate.getPublicKey().getEncoded());
                        if (isItMe) {
                            deferred.reject(new ShamefulSelfConnectException());
                        } else {
                            Certificate result = certificateManager.importCertificate(x509Certificate, certificate.getName().v(), null, address, port, portCert, certificate.getGreeting().v());
                            deferred.resolve(result);
                            // lock.unlockWrite();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        deferred.reject(e);
                    } finally {
                        stop();
                    }
                }

                @Override
                public void onOpen() {

                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                    deferred.reject(ex);
                    stop();
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    clientSockets.remove(this);
                    stop();
                }

                @Override
                public void onBlockReceived(BlockReceivedJob block) {

                }
            });

            socket.connect(new InetSocketAddress(address, portCert));
            certDeliveryClient.setSocket(socket);
            certDeliveryClient.start();

            clientSockets.put(certDeliveryClient, 1);
            MeinMessage meinMessage = MeinAuthCertDelivery.createCertDeliveryGet();
            String json = SerializableEntitySerializer.serialize(meinMessage);
            certDeliveryClient.send(json);
            // lock.lockWrite();
        } catch (Exception e) {
            e.printStackTrace();
            if (!deferred.isRejected())
                deferred.reject(e);
        }
    }
}

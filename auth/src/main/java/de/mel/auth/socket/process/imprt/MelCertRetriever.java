package de.mel.auth.socket.process.imprt;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.data.MelMessage;
import de.mel.auth.data.MelResponse;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.socket.MelSocket;
import de.mel.auth.socket.ShamefulSelfConnectException;
import de.mel.auth.tools.ShutDownDeferredManager;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * transfers certificates and imports them into the database
 * Created by xor on 4/18/16.
 */
public class MelCertRetriever extends DeferredRunnable {
    private final MelAuthServiceImpl melAuthService;
    private final CertificateManager certificateManager;
    private Map<MelSocket, Object> clientSockets = new ConcurrentHashMap<>();
    private DeferredObject<Certificate, Exception, Object> deferred;
    private int portCert;
    private String address;
    private int port;
    private MelSocket certDeliveryClient;

    public MelCertRetriever(MelAuthServiceImpl melAuthService) {
        this.melAuthService = melAuthService;
        this.certificateManager = melAuthService.getCertificateManager();
    }

    public DeferredObject<Certificate, Exception, Object> retrieveCertificate(DeferredObject<Certificate, Exception, Object> deferred, String address, int port, int portCert) throws URISyntaxException, InterruptedException {
        this.address = address;
        this.port = port;
        this.portCert = portCert;
        this.deferred = deferred;
        melAuthService.execute(this);
        return deferred;
    }


    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        ShutDownDeferredManager shutDownDeferredManager = new ShutDownDeferredManager();
        shutDownDeferredManager.when(certDeliveryClient.shutDown());
        for (MelSocket socket : clientSockets.keySet()) {
            shutDownDeferredManager.when(socket.shutDown());
        }
        return shutDownDeferredManager.digest();
    }

    @Override
    public void runImpl() {
        try {
            // RWLock lock = new RWLock();
            //lock.lockWrite();
            Lok.error("MelCertRetriever.retrieveCertificate.ALPHONSO");
            URI uri = new URI(address);

            Socket socket = new Socket();
            certDeliveryClient = new MelSocket(melAuthService, socket);
            certDeliveryClient.setListener(new MelSocket.MelSocketListener() {
                @Override
                public void onIsolated() {

                }

                @Override
                public void onMessage(MelSocket melSocket, String messageString) {
                    Lok.debug(melAuthService.getName() + ".MelCertRetriever.onMessage.got: " + messageString);
                    try {
                        SerializableEntityDeserializer deserializer = new SerializableEntityDeserializer();
                        MelResponse response = (MelResponse) SerializableEntityDeserializer.deserialize(messageString);
                        Certificate certificate = response.getCertificate();
                        X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(certificate.getCertificate().v());
                        boolean isItMe = Arrays.equals(certificateManager.getMyX509Certificate().getPublicKey().getEncoded(), x509Certificate.getPublicKey().getEncoded());
                        if (isItMe) {
                            deferred.reject(new ShamefulSelfConnectException());
                        } else {
                            Certificate result = certificateManager.importCertificate(x509Certificate, certificate.getName().v(), null, address, port, portCert);
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
            MelMessage melMessage = MelAuthCertDelivery.createCertDeliveryGet();
            String json = SerializableEntitySerializer.serialize(melMessage);
            certDeliveryClient.send(json);
            // lock.lockWrite();
        } catch (Exception e) {
            e.printStackTrace();
            if (!deferred.isRejected())
                deferred.reject(e);
        }
    }
}

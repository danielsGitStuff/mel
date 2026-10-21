package de.mel.auth.socket.process.imprt;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.data.MelMessage;
import de.mel.auth.data.MelResponse;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.CloseReason;
import de.mel.auth.socket.MelSocket;
import de.mel.auth.socket.ShamefulSelfConnectException;
import de.mel.auth.tools.Promises;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * transfers certificates and imports them into the database
 * Created by xor on 4/18/16.
 */
public class MelCertRetriever extends DeferredRunnable {
    private final MelAuthService melAuthService;
    private final CertificateManager certificateManager;
    private Map<MelSocket, Object> clientSockets = new ConcurrentHashMap<>();
    private PromiseImpl<Certificate, Exception> deferred;
    private int portCert;
    private String address;
    private int port;
    private MelSocket certDeliveryClient;

    public MelCertRetriever(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
        this.certificateManager = melAuthService.getCertificateManager();
    }

    public PromiseImpl<Certificate, Exception> retrieveCertificate(PromiseImpl<Certificate, Exception> deferred, String address, int port, int portCert) throws URISyntaxException, InterruptedException {
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
    public Promise<Void, Void> onShutDown() {
        List<Promise<?, ?>> shutDownDeferredManager = new ArrayList<>();
        shutDownDeferredManager.add(certDeliveryClient.shutDown());
        for (MelSocket socket : clientSockets.keySet()) {
            shutDownDeferredManager.add(socket.shutDown());
        }
        return Promises.allSettled(shutDownDeferredManager.toArray(new Promise[0]));
    }

    @Override
    public void runImpl() {
        try {
            // RWLock lock = new RWLock();
            //lock.lockWrite();
            Lok.error("MelCertRetriever.retrieveCertificate.ALPHONSO");
            Socket socket = new Socket();
            certDeliveryClient = new MelSocket(melAuthService, socket);
            certDeliveryClient.setListener(new MelSocket.MelSocketListener() {

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
                            deferred.resolveError(new ShamefulSelfConnectException());
                        } else {
                            Certificate result = certificateManager.importCertificate(x509Certificate, certificate.getName().v(), address, port, portCert);
                            deferred.resolveOk(result);
                            // lock.unlockWrite();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        deferred.resolveError(e);
                    } finally {
                        stop();
                        melSocket.close(CloseReason.FINISHED);
                    }
                }

                @Override
                public void onOpen() {

                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                    deferred.resolveError(ex);
                    stop();
                }

                @Override
                public void onClose(CloseReason reason, boolean remote) {
                    clientSockets.remove(certDeliveryClient);
                    if (deferred.isPending())
                        deferred.resolveError(new Exception("the certificate delivery closed the connection without an answer: " + reason));
                    stop();
                }

                @Override
                public void onBlockReceived(BlockReceivedJob block) {

                }
            });

            socket.connect(new InetSocketAddress(address, portCert), (int) melAuthService.getConnectTimeoutMs());
            certDeliveryClient.setSocket(socket);
            certDeliveryClient.start();

            clientSockets.put(certDeliveryClient, 1);
            MelMessage melMessage = MelAuthCertDelivery.createCertDeliveryGet();
            String json = SerializableEntitySerializer.serialize(melMessage);
            // the delivery answers at once. If it does not, nobody waits forever.
            MelSocket waitingSocket = certDeliveryClient;
            melAuthService.getTimeoutScheduler().schedule(() -> {
                if (deferred.isPending()) {
                    deferred.resolveError(new Exception("the certificate delivery at " + address + ":" + portCert + " did not answer"));
                    waitingSocket.close(CloseReason.TIMEOUT);
                }
            }, melAuthService.getStateTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            certDeliveryClient.send(json);
            // lock.lockWrite();
        } catch (Exception e) {
            e.printStackTrace();
            if (deferred.isPending())
                deferred.resolveError(e);
            if (certDeliveryClient != null)
                certDeliveryClient.close(CloseReason.IO_ERROR);
        }
    }
}

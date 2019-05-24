package de.mein.auth.socket;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.jobs.ConnectJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.imprt.MeinCertRetriever;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.Hash;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.SSLSocket;

import java.io.IOException;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;


/**
 * Created by xor on 10.08.2016.
 */
@SuppressWarnings("Duplicates")
public class MeinAuthSocket extends MeinSocket implements MeinSocket.MeinSocketListener {


    protected MeinProcess process;
    protected Certificate partnerCertificate;
    private AConnectJob connectJob;

    public AConnectJob getConnectJob() {
        return connectJob;
    }

    public MeinAuthSocket(MeinAuthService meinAuthService) {
        super(meinAuthService, null);
        setListener(this);
    }

    public Certificate getPartnerCertificate() {
        return partnerCertificate;
    }

    MeinAuthSocket setProcess(MeinProcess process) {
        this.process = process;
        return this;
    }

    public String getAddressString() {
        return MeinAuthSocket.getAddressString(socket.getInetAddress(), socket.getPort());
    }

    public static String getAddressString(InetAddress address, int port) {
        return address.getHostAddress() + ":" + port;
    }

    public MeinAuthSocket(MeinAuthService meinAuthService, Socket socket) {
        super(meinAuthService, socket);
        setListener(this);
    }

    public MeinAuthSocket(MeinAuthService meinAuthService, AConnectJob connectJob) {
        super(meinAuthService, null);
        this.connectJob = connectJob;
        setListener(this);
    }

    public MeinAuthSocket allowIsolation() {
        this.allowIsolation = true;
        return this;
    }

    @Override
    public void onIsolated() {
        ((MeinIsolatedProcess) process).onIsolated();
    }

    @Override
    public void onMessage(MeinSocket meinSocket, String msg) {
        try {
            meinAuthService.getPowerManager().wakeLock(this);
            SerializableEntity deserialized = SerializableEntityDeserializer.deserialize(msg);
            //todo debug
            if (deserialized instanceof MeinRequest) {
                MeinRequest request = (MeinRequest) deserialized;
                if (request.getAuthenticated() != null && request.getAuthenticated()) {
                    Lok.debug("MeinAuthSocket.onMessage.9djg90areh0g");
                    Lok.debug("MeinAuthSocket.onMessage.ij3g89wh9543w");
                }
            }
            if (process != null) {
                process.onMessageReceived(deserialized, this);
            } else if (deserialized instanceof MeinRequest) {
                MeinRequest request = (MeinRequest) deserialized;
                if (request.getServiceUuid().equals(MeinStrings.SERVICE_NAME) &&
                        request.getIntent().equals(MeinStrings.msg.INTENT_REGISTER)) {
                    MeinRegisterProcess meinRegisterProcess = new MeinRegisterProcess(this);
                    process = meinRegisterProcess;
                    meinRegisterProcess.onMessageReceived(deserialized, this);
                } else if (request.getServiceUuid().equals(MeinStrings.SERVICE_NAME) &&
                        request.getIntent().equals(MeinStrings.msg.INTENT_AUTH)) {
                    MeinAuthProcess meinAuthProcess = new MeinAuthProcess(this);
                    process = meinAuthProcess;
                    meinAuthProcess.onMessageReceived(deserialized, this);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            meinAuthService.getPowerManager().releaseWakeLock(this);

        }
    }

    @Override
    public void onOpen() {

    }

    @Override
    public void onError(Exception ex) {

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Lok.debug(meinAuthService.getName() + "." + getClass().getSimpleName() + ".onClose");
        process.onSocketClosed(code, reason, remote);
        meinAuthService.onSocketClosed(this);
    }

    @Override
    public void onBlockReceived(BlockReceivedJob block) {
        // this shall only work with isolated processes
        ((MeinIsolatedProcess) process).onBlockReceived(block);
    }

    void connectSSL(Long certId, String address, int port) throws SqlQueriesException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        if (certId != null)
            partnerCertificate = meinAuthService.getCertificateManager().getTrustedCertificateById(certId);
        Socket socket = meinAuthService.getCertificateManager().createSocket();
        Lok.debug("MeinAuthSocket.connectSSL: " + address + ":" + port);
        socket.connect(new InetSocketAddress(address, port));
        //stop();
        setSocket(socket);
        start();
    }

    Certificate getTrustedPartnerCertificate() throws IOException, CertificateEncodingException, SqlQueriesException, ShamefulSelfConnectException {
        if (partnerCertificate == null) {
            SSLSocket sslSocket = (SSLSocket) socket;
            java.security.cert.Certificate cert = sslSocket.getSession().getPeerCertificates()[0];
            byte[] certBytes = cert.getEncoded();
            String hash = Hash.sha256(certBytes);
            // todo debug
            List<Certificate> allCerts = meinAuthService.getCertificateManager().getAllCertificateDetails();
            for (Certificate certificate : allCerts) {
                Lok.debug("avail cert: id: " + certificate.getId().v() + " , name: " + certificate.getName().v() + " ,hash: " + certificate.getHash().v() + " ,trusted: " + certificate.getTrusted().v());
            }
            partnerCertificate = meinAuthService.getCertificateManager().getTrustedCertificateByHash(hash);
            if (partnerCertificate == null) {
                if (Arrays.equals(meinAuthService.getCertificateManager().getPublicKey().getEncoded(), cert.getPublicKey().getEncoded())) {
                    throw new ShamefulSelfConnectException();
                }
            }
        }
        return partnerCertificate;
    }


    public void sendBlock(byte[] block) throws IOException {
        assert block.length == MeinSocket.BLOCK_SIZE;
        out.write(block);
        out.flush();
    }

    public void disconnect() throws IOException {
        socket.close();
    }

    public boolean isValidated() {
        return (process != null && process instanceof MeinValidationProcess);
    }

    @Override
    protected void onSocketClosed() {
        meinAuthService.onSocketClosed(this);
    }

    public MeinProcess getProcess() {
        return process;
    }

    @Override
    public void onShutDown() {
        super.onShutDown();
    }


    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    Socket getSocket() {
        return socket;
    }
}

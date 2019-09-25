package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.sql.Hash;
import de.mel.sql.SqlQueriesException;

import org.jdeferred.impl.DeferredObject;

import javax.net.ssl.SSLSocket;

import java.io.IOException;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;


/**
 * Created by xor on 10.08.2016.
 */
@SuppressWarnings("Duplicates")
public class MelAuthSocket extends MelSocket implements MelSocket.MelSocketListener {

    protected MelProcess process;
    protected Certificate partnerCertificate;
    private AConnectJob connectJob;

    public MelAuthSocket(MelAuthService melAuthService) {
        super(melAuthService, null);
        setListener(this);
    }

    public MelAuthSocket(MelAuthService melAuthService, Socket socket) {
        super(melAuthService, socket);
        setListener(this);
    }

    public MelAuthSocket(MelAuthService melAuthService, AConnectJob connectJob) {
        super(melAuthService, null);
        this.connectJob = connectJob;
        setListener(this);
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
                return socket.getLocalPort();
            return connectJob.getPort();
        });
        return MelAuthSocket.getAddressString(socket.getInetAddress(), port);
    }

    public MelAuthSocket allowIsolation() {
        this.allowIsolation = true;
        return this;
    }

    @Override
    public void onIsolated() {
        ((MelIsolatedProcess) process).onIsolated();
    }

    @Override
    public void onMessage(MelSocket melSocket, String msg) {
        try {
            melAuthService.getPowerManager().wakeLock(this);
            SerializableEntity deserialized = SerializableEntityDeserializer.deserialize(msg);
            if (process != null) {
                process.onMessageReceived(deserialized, this);
            } else if (deserialized instanceof MelRequest) {
                MelRequest request = (MelRequest) deserialized;
                if (request.getServiceUuid().equals(MelStrings.SERVICE_NAME) &&
                        request.getIntent().equals(MelStrings.msg.INTENT_REGISTER)) {
                    MelRegisterProcess melRegisterProcess = new MelRegisterProcess(this);
                    process = melRegisterProcess;
                    melRegisterProcess.onMessageReceived(deserialized, this);
                } else if (request.getServiceUuid().equals(MelStrings.SERVICE_NAME) &&
                        request.getIntent().equals(MelStrings.msg.INTENT_AUTH)) {
                    MelAuthProcess melAuthProcess = new MelAuthProcess(this);
                    process = melAuthProcess;
                    melAuthProcess.onMessageReceived(deserialized, this);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            melAuthService.getPowerManager().releaseWakeLock(this);

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
//        Lok.debug(melAuthService.getName() + "." + getClass().getSimpleName() + ".onClose");
        if (process != null)
            process.onSocketClosed(code, reason, remote);
        melAuthService.onSocketClosed(this);
    }

    @Override
    public void onBlockReceived(BlockReceivedJob block) {
        // this shall only work with isolated processes
        ((MelIsolatedProcess) process).onBlockReceived(block);
    }

    void connectSSL(Long certId, String address, int port) throws SqlQueriesException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        if (certId != null)
            partnerCertificate = melAuthService.getCertificateManager().getTrustedCertificateById(certId);
        Socket socket = melAuthService.getCertificateManager().createSocket();
        Lok.debug("MelAuthSocket.connectSSL: " + address + ":" + port);
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
            partnerCertificate = melAuthService.getCertificateManager().getTrustedCertificateByHash(hash);
            if (partnerCertificate == null) {
                if (Arrays.equals(melAuthService.getCertificateManager().getPublicKey().getEncoded(), cert.getPublicKey().getEncoded())) {
                    throw new ShamefulSelfConnectException();
                }
            }
        }
        return partnerCertificate;
    }

    public void sendBlock(byte[] block) throws IOException {
        assert block.length == MelSocket.BLOCK_SIZE;
        out.write(block);
        out.flush();
    }

    public void disconnect() throws IOException {
        socket.close();
    }

    public boolean isValidated() {
        return (process != null && process instanceof MelValidationProcess);
    }

    @Override
    protected void onSocketClosed() {
        melAuthService.onSocketClosed(this);
    }

    public MelProcess getProcess() {
        return process;
    }

    MelAuthSocket setProcess(MelProcess process) {
        this.process = process;
        return this;
    }

    @Override
    public DeferredObject<Void, Void, Void> onShutDown() {
        super.onShutDown();
        return null;
    }


    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    public Socket getSocket() {
        //todo make package private
        return socket;
    }
}

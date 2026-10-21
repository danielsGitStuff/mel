package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.*;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.socket.process.reg.IRegisteredHandler;
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;
import org.jdeferred.multiple.OneReject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * handles registration of incoming and outgoing connections
 * Created by xor on 4/20/16.
 */
public class MelRegisterProcess extends MelProcess {
    private CertificateManager certificateManager = null;


    private N runner = new N(Throwable::printStackTrace);

    private void log(String msg) {
        Lok.debug("[" + Thread.currentThread().getName() + "] " + melAuthSocket.getMelAuthService().getName() + ".MelRegisterProcess: " + msg);
    }

    private DeferredObject<MelRegisterConfirm, Exception, Void> confirmedPromise = new DeferredObject<>();
    private DeferredObject<Certificate, Exception, Void> acceptedPromise = new DeferredObject<>();

    public MelRegisterProcess(MelAuthSocket melAuthSocket) {
        super(melAuthSocket);
        this.certificateManager = melAuthSocket.getMelAuthService().getCertificateManager();
        confirmedPromise.done(result -> {
            for (IRegisterHandler registerHandler : melAuthSocket.getMelAuthService().getRegisterHandlers())
                registerHandler.onRemoteAccepted(partnerCertificate);
        });
        acceptedPromise.done(result -> {
            for (IRegisterHandler registerHandler : melAuthSocket.getMelAuthService().getRegisterHandlers())
                registerHandler.onLocallyAccepted(partnerCertificate);
        });
        new DefaultDeferredManager().when(confirmedPromise, acceptedPromise).done(results -> runner.runTry(() -> {
                    Lok.debug(melAuthSocket.getMelAuthService().getName() + ".MelRegisterProcess.MelRegisterProcess");
                    Certificate certificate = (Certificate) results.get(1).getResult();
                    certificateManager.updateCertificate(certificate);
                    certificateManager.trustCertificate(certificate.getId().v(), true);
                    for (IRegisterHandler registerHandler : melAuthSocket.getMelAuthService().getRegisterHandlers())
                        registerHandler.onRegistrationCompleted(partnerCertificate);
                    for (IRegisteredHandler handler : melAuthSocket.getMelAuthService().getRegisteredHandlers()) {
                        handler.onCertificateRegistered(melAuthSocket.getMelAuthService(), certificate);
                    }
                    //todo send done, so the other side can connect!
                })
        ).fail(results -> runner.runTry(() -> {
            if (results instanceof OneReject) {
                if (results.getReject() instanceof PartnerDidNotTrustException) {
                    Lok.debug("MelRegisterProcess.MelRegisterProcess.rejected: " + results.getReject().toString());
                    for (IRegisterHandler registerHandler : melAuthSocket.getMelAuthService().getRegisterHandlers())
                        registerHandler.onRemoteRejected(partnerCertificate);
                } else if (results.getReject() instanceof UserDidNotTrustException) {
                    Lok.debug("MelRegisterProcess.MelRegisterProcess.user.did.not.trust");
                    for (IRegisterHandler registerHandler : melAuthSocket.getMelAuthService().getRegisterHandlers())
                        registerHandler.onLocallyRejected(partnerCertificate);
                }
                certificateManager.deleteCertificate(partnerCertificate);
            } else {
                Lok.error("MelRegisterProcess.MelRegisterProcess.reject.UNKNOWN.2");
                certificateManager.deleteCertificate(partnerCertificate);
            }

            melAuthSocket.close(CloseReason.REJECTED);
        }));
    }

    public Promise<Certificate, Exception, Void> register(DeferredObject<Certificate, Exception, Void> deferred, Long id, String address, int port) throws InterruptedException, SqlQueriesException, CertificateException, NoSuchPaddingException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, ClassNotFoundException, JsonSerializationException, IllegalAccessException, URISyntaxException, UnrecoverableKeyException, KeyStoreException, KeyManagementException {
        Lok.debug(melAuthSocket.getMelAuthService().getName() + ".MelRegisterProcessImpl.register.id=" + id);
        melAuthSocket.setState(new SocketState.Registering(this));
        melAuthSocket.connectSSL(id, address, port);
        partnerCertificate = melAuthSocket.getMelAuthService().getCertificateManager().getCertificateById(id);
        Certificate myCert = new Certificate();
        myCert.setName(melAuthSocket.getMelAuthService().getName());
        myCert.setCertificate(melAuthSocket.getMelAuthService().getCertificateManager().getMyX509Certificate().getEncoded());
        MelAuthSettings settings = melAuthSocket.getMelAuthService().getSettings();
        myCert.setPort(settings.getPort())
                .setCertDeliveryPort(settings.getDeliveryPort());
        MelRequest request = new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_REGISTER)
                .setCertificate(myCert)
                .setRequestHandler(this).queue();


        new DefaultDeferredManager().when(confirmedPromise, acceptedPromise).done(result -> {
            Lok.debug("MelRegisterProcess.register");
            deferred.resolve(partnerCertificate);
        });
        String json = SerializableEntitySerializer.serialize(request);
        log("sending registration request");
        this.melAuthSocket.send(json);
        for (IRegisterHandler regHandler : melAuthSocket.getMelAuthService().getRegisterHandlers()) {
            log("asking register handler " + regHandler.getClass().getSimpleName());
            regHandler.acceptCertificate(new IRegisterHandlerListener() {
                @Override
                public void onCertificateAccepted(MelRequest request, Certificate certificate) {
                    runner.runTry(() -> {
                        // tell your partner that you trust him
                        MelRegisterProcess.this.sendConfirmation(true);
                        partnerCertificate = certificate;
                        acceptedPromise.resolve(certificate);
                    });

                }

                @Override
                public void onCertificateRejected(MelRequest request, Certificate certificate) {
                    runner.runTry(() -> {
                        MelRegisterProcess.this.sendConfirmation(false);
                        acceptedPromise.reject(new UserDidNotTrustException());
                    });
                }
            }, request, melAuthSocket.getMelAuthService().getMyCertificate(), partnerCertificate);
        }
        return deferred;
    }

    public void sendConfirmation(boolean trusted) throws JsonSerializationException, IllegalAccessException {
        log("sending confirmation, trusted=" + trusted);
        MelMessage message = new MelMessage(MelStrings.SERVICE_NAME, null);
        message.setPayLoad(new MelRegisterConfirm().setConfirmed(trusted));
        send(message);
    }

    @Override
    public void onMessageReceived(SerializableEntity deserialized, MelAuthSocket webSocket) {
        if (this.melAuthSocket == null)
            this.melAuthSocket = webSocket;
        if (!handleAnswer(deserialized))
            if (deserialized instanceof MelRequest) {
                MelRequest request = (MelRequest) deserialized;
                log("received registration request");
                try {
                    Certificate certificate = request.getCertificate();
                    // not imported yet, but from now on there is somebody to tell the handlers about and to clean up after
                    partnerCertificate = certificate;
                    if (melAuthSocket.getMelAuthService().getRegisterHandlers().size() == 0) {
                        Lok.debug("MelRegisterProcess.onMessageReceived.NO.HANDLER.FOR.REGISTRATION.AVAILABLE");
                    }
                    for (IRegisterHandler registerHandler : melAuthSocket.getMelAuthService().getRegisterHandlers()) {
                        registerHandler.acceptCertificate(new IRegisterHandlerListener() {
                            @Override
                            public void onCertificateAccepted(MelRequest request, Certificate certificate) {
                                runner.runTry(() -> {
                                    if (confirmedPromise.isRejected()) {
                                        // the partner said no while the user was thinking, the registration is over
                                        return;
                                    }
                                    // The certificate is imported but not trusted. That happens when the partner has confirmed, too (see constructor).
                                    // Trusting it here would leave somebody trusted who rejected us in the meantime.
                                    X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(certificate.getCertificate().v());
                                    String address = melAuthSocket.getAddress();
                                    int port = certificate.getPort().v();
                                    int portCert = certificate.getCertDeliveryPort().v();
                                    partnerCertificate = certificateManager.importCertificate(x509Certificate, certificate.getName().v(), address, port, portCert);
                                    if (confirmedPromise.isRejected()) {
                                        // rejected between the check and the import: nobody cleaned up the certificate, because it did not exist yet
                                        certificateManager.deleteCertificate(partnerCertificate);
                                        return;
                                    }
                                    // If the partner confirmed already, this trusts it right here. Only then tell it, because
                                    // it is done once it knows and connects (on another socket) at once.
                                    acceptedPromise.resolve(partnerCertificate);
                                    // tell your partner that you appreciate her actions!
                                    MelRegisterProcess.this.sendConfirmation(true);
                                });
                            }

                            @Override
                            public void onCertificateRejected(MelRequest request, Certificate certificate) {
                                runner.runTry(() -> {
                                    MelRegisterProcess.this.sendConfirmation(false);
                                    acceptedPromise.reject(new UserDidNotTrustException());
                                });
                            }
                        }, request, melAuthSocket.getMelAuthService().getMyCertificate(), certificate);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (deserialized instanceof MelResponse) {
                //should not be called
                Lok.debug("MelRegisterProcess.onMessageReceived.WRONG");
            } else if (deserialized instanceof MelMessage) {
                MelMessage message = (MelMessage) deserialized;
                if (message.getPayload() != null && message.getPayload() instanceof MelRegisterConfirm) {
                    MelRegisterConfirm confirm = (MelRegisterConfirm) message.getPayload();
                    log("received confirmation, confirmed=" + confirm.isConfirmed());
                    if (confirm.isConfirmed()) {
                        confirmedPromise.resolve(confirm);
                    } else {
                        confirmedPromise.reject(new PartnerDidNotTrustException());
                    }
                }
            } else
                Lok.debug("MelRegisterProcess.onMessageReceived.VERY.WRONG");
    }
}

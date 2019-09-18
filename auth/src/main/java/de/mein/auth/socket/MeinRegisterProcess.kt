package de.mein.auth.socket;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.*;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.sql.SqlQueriesException;
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
import java.util.UUID;

/**
 * handles registration of incoming and outgoing connections
 * Created by xor on 4/20/16.
 */
public class MeinRegisterProcess extends MeinProcess {
    private CertificateManager certificateManager = null;


    private N runner = new N(Throwable::printStackTrace);
    private DeferredObject<MeinRegisterConfirm, Exception, Void> confirmedPromise = new DeferredObject<>();
    private DeferredObject<Certificate, Exception, Void> acceptedPromise = new DeferredObject<>();

    public MeinRegisterProcess(MeinAuthSocket meinAuthSocket) {
        super(meinAuthSocket);
        this.certificateManager = meinAuthSocket.getMeinAuthService().getCertificateManager();
        confirmedPromise.done(result -> {
            for (IRegisterHandler registerHandler : meinAuthSocket.getMeinAuthService().getRegisterHandlers())
                registerHandler.onRemoteAccepted(partnerCertificate);
        });
        acceptedPromise.done(result -> {
            for (IRegisterHandler registerHandler : meinAuthSocket.getMeinAuthService().getRegisterHandlers())
                registerHandler.onLocallyAccepted(partnerCertificate);
        });
        new DefaultDeferredManager().when(confirmedPromise, acceptedPromise).done(results -> runner.runTry(() -> {
                    Lok.debug(meinAuthSocket.getMeinAuthService().getName() + ".MeinRegisterProcess.MeinRegisterProcess");
                    MeinRegisterConfirm confirm = (MeinRegisterConfirm) results.get(0).getResult();
                    Certificate certificate = (Certificate) results.get(1).getResult();
                    //check if is UUID
                    UUID answerUuid = UUID.fromString(confirm.getAnswerUuid());
                    certificate.getAnswerUuid().v(answerUuid.toString());
                    certificateManager.updateCertificate(certificate);
                    certificateManager.trustCertificate(certificate.getId().v(), true);
                    for (IRegisterHandler registerHandler : meinAuthSocket.getMeinAuthService().getRegisterHandlers())
                        registerHandler.onRegistrationCompleted(partnerCertificate);
                    for (IRegisteredHandler handler : meinAuthSocket.getMeinAuthService().getRegisteredHandlers()) {
                        handler.onCertificateRegistered(meinAuthSocket.getMeinAuthService(), certificate);
                    }
                    //todo send done, so the other side can connect!
                })
        ).fail(results -> runner.runTry(() -> {
            if (results instanceof OneReject) {
                if (results.getReject() instanceof PartnerDidNotTrustException) {
                    Lok.debug("MeinRegisterProcess.MeinRegisterProcess.rejected: " + results.getReject().toString());
                    for (IRegisterHandler registerHandler : meinAuthSocket.getMeinAuthService().getRegisterHandlers())
                        registerHandler.onRemoteRejected(partnerCertificate);
                } else if (results.getReject() instanceof UserDidNotTrustException) {
                    Lok.debug("MeinRegisterProcess.MeinRegisterProcess.user.did.not.trust");
                    for (IRegisterHandler registerHandler : meinAuthSocket.getMeinAuthService().getRegisterHandlers())
                        registerHandler.onLocallyRejected(partnerCertificate);
                }
                certificateManager.deleteCertificate(partnerCertificate);
            } else {
                Lok.error("MeinRegisterProcess.MeinRegisterProcess.reject.UNKNOWN.2");
                certificateManager.deleteCertificate(partnerCertificate);
            }

            stop();
        }));
    }

    public Promise<Certificate, Exception, Void> register(DeferredObject<Certificate, Exception, Void> deferred, Long id, String address, int port) throws InterruptedException, SqlQueriesException, CertificateException, NoSuchPaddingException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, ClassNotFoundException, JsonSerializationException, IllegalAccessException, URISyntaxException, UnrecoverableKeyException, KeyStoreException, KeyManagementException {
        Lok.debug(meinAuthSocket.getMeinAuthService().getName() + ".MeinRegisterProcessImpl.register.id=" + id);
        meinAuthSocket.connectSSL(id, address, port);
        partnerCertificate = meinAuthSocket.getMeinAuthService().getCertificateManager().getCertificateById(id);
        Certificate myCert = new Certificate();
        myCert.setName(meinAuthSocket.getMeinAuthService().getName());
        myCert.setAnswerUuid(partnerCertificate.getUuid().v());
        myCert.setCertificate(meinAuthSocket.getMeinAuthService().getCertificateManager().getMyX509Certificate().getEncoded());
        MeinAuthSettings settings = meinAuthSocket.getMeinAuthService().getSettings();
        myCert.setPort(settings.getPort())
                .setCertDeliveryPort(settings.getDeliveryPort());
        MeinRequest request = new MeinRequest(MeinStrings.SERVICE_NAME, MeinStrings.msg.INTENT_REGISTER)
                .setCertificate(myCert)
                .setRequestHandler(this).queue();
        for (IRegisterHandler regHandler : meinAuthSocket.getMeinAuthService().getRegisterHandlers()) {
            regHandler.acceptCertificate(new IRegisterHandlerListener() {
                @Override
                public void onCertificateAccepted(MeinRequest request, Certificate certificate) {
                    runner.runTry(() -> {
                        // tell your partner that you trust him
                        MeinRegisterProcess.this.sendConfirmation(true);
                        partnerCertificate = certificate;
                        acceptedPromise.resolve(certificate);
                    });

                }

                @Override
                public void onCertificateRejected(MeinRequest request, Certificate certificate) {
                    runner.runTry(() -> {
                        MeinRegisterProcess.this.sendConfirmation(false);
                        acceptedPromise.reject(new UserDidNotTrustException());
                    });
                }
            }, request, meinAuthSocket.getMeinAuthService().getMyCertificate(), partnerCertificate);

        }
        request.getAnswerDeferred().done(result -> {
            MeinRequest r = (MeinRequest) result;
            Certificate certificate = r.getCertificate();
            try {
                partnerCertificate = meinAuthSocket.getMeinAuthService().getCertificateManager().addAnswerUuid(partnerCertificate.getId().v(), certificate.getAnswerUuid().v());
                MeinResponse response = r.reponse();
                response.setState(MeinStrings.msg.STATE_OK);
                send(response);
                MeinRegisterProcess.this.removeThyself();
                for (IRegisteredHandler registeredHandler : meinAuthSocket.getMeinAuthService().getRegisteredHandlers()) {
                    try {
                        registeredHandler.onCertificateRegistered(meinAuthSocket.getMeinAuthService(), partnerCertificate);
                    } catch (SqlQueriesException e) {
                        e.printStackTrace();
                    }
                }
                deferred.resolve(partnerCertificate);
            } catch (Exception e) {
                e.printStackTrace();
                deferred.reject(e);
            }
        }).fail(result -> {
            Lok.debug("MeinRegisterProcess.onFail!!!!!!!!");
            deferred.reject(result);
        });
        new DefaultDeferredManager().when(confirmedPromise, acceptedPromise).done(result -> {
            Lok.debug("MeinRegisterProcess.register");
            deferred.resolve(partnerCertificate);
        });
        String json = SerializableEntitySerializer.serialize(request);
        this.meinAuthSocket.send(json);
        return deferred;
    }

    public void sendConfirmation(boolean trusted) throws JsonSerializationException, IllegalAccessException {
        MeinMessage message = new MeinMessage(MeinStrings.SERVICE_NAME, null);
        if (trusted)
            message.setPayLoad(new MeinRegisterConfirm().setConfirmed(trusted).setAnswerUuid(partnerCertificate.getUuid().v()));
        else
            message.setPayLoad(new MeinRegisterConfirm().setConfirmed(false));
        send(message);
    }

    @Override
    public void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket webSocket) {
        if (this.meinAuthSocket == null)
            this.meinAuthSocket = webSocket;
        if (!handleAnswer(deserialized))
            if (deserialized instanceof MeinRequest) {
                MeinRequest request = (MeinRequest) deserialized;
                try {
                    Certificate certificate = request.getCertificate();
                    if (meinAuthSocket.getMeinAuthService().getRegisterHandlers().size() == 0) {
                        Lok.debug("MeinRegisterProcess.onMessageReceived.NO.HANDLER.FOR.REGISTRATION.AVAILABLE");
                    }
                    for (IRegisterHandler registerHandler : meinAuthSocket.getMeinAuthService().getRegisterHandlers()) {
                        registerHandler.acceptCertificate(new IRegisterHandlerListener() {
                            @Override
                            public void onCertificateAccepted(MeinRequest request, Certificate certificate) {
                                runner.runTry(() -> {
                                    // tell your partner that you appreciate her actions!
                                    X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(certificate.getCertificate().v());
                                    String address = meinAuthSocket.getAddress();
                                    int port = certificate.getPort().v();
                                    int portCert = certificate.getCertDeliveryPort().v();
                                    partnerCertificate = certificateManager.importCertificate(x509Certificate, certificate.getName().v(), certificate.getAnswerUuid().v(), address, port, portCert);
                                    certificateManager.trustCertificate(partnerCertificate.getId().v(), true);
                                    MeinRegisterProcess.this.sendConfirmation(true);
                                    for (IRegisteredHandler handler : meinAuthSocket.getMeinAuthService().getRegisteredHandlers()) {
                                        handler.onCertificateRegistered(meinAuthSocket.getMeinAuthService(), partnerCertificate);
                                    }
                                    acceptedPromise.resolve(partnerCertificate);
                                });
                            }

                            @Override
                            public void onCertificateRejected(MeinRequest request, Certificate certificate) {
                                runner.runTry(() -> {
                                    MeinRegisterProcess.this.sendConfirmation(false);
                                    acceptedPromise.reject(new PartnerDidNotTrustException());
                                });
                            }
                        }, request, meinAuthSocket.getMeinAuthService().getMyCertificate(), certificate);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (deserialized instanceof MeinResponse) {
                //should not be called
                Lok.debug("MeinRegisterProcess.onMessageReceived.WRONG");
            } else if (deserialized instanceof MeinMessage) {
                MeinMessage message = (MeinMessage) deserialized;
                if (message.getPayload() != null && message.getPayload() instanceof MeinRegisterConfirm) {
                    MeinRegisterConfirm confirm = (MeinRegisterConfirm) message.getPayload();
                    if (confirm.isConfirmed()) {
                        confirmedPromise.resolve(confirm);
                    } else {
                        confirmedPromise.reject(new PartnerDidNotTrustException());
                    }
                }
            } else
                Lok.debug("MeinRegisterProcess.onMessageReceived.VERY.WRONG");
    }
}

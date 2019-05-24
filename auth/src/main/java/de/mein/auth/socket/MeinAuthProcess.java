package de.mein.auth.socket;

import org.jdeferred.Promise;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.IsolationDetails;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.MeinResponse;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.ConnectJob;
import de.mein.auth.jobs.IsolatedConnectJob;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.socket.process.val.MeinServicesPayload;
import de.mein.auth.tools.Cryptor;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.SqlQueriesException;

/**
 * handles authentication of incoming and outgoing connections
 * Created by xor on 4/21/16.
 */
@SuppressWarnings("Duplicates")
public class MeinAuthProcess extends MeinProcess {
    private String mySecret;
    // this is not mine
    private String decryptedSecret;
    private boolean partnerAuthenticated = false;
    private MeinValidationProcess validationProcess;

    public MeinAuthProcess(MeinAuthSocket meinAuthSocket) {
        super(meinAuthSocket);
    }

    /**
     * adds the currently available services to the Response
     *
     * @param meinAuthService
     * @param partnerCertificate
     * @param response
     * @throws SqlQueriesException
     */
    public static void addAllowedServices(MeinAuthService meinAuthService, Certificate partnerCertificate, MeinResponse response) throws SqlQueriesException {
        MeinServicesPayload payload = meinAuthService.getAllowedServicesFor(partnerCertificate.getId().v());
        response.setPayLoad(payload);
    }

    @Override
    public void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket socket) {
        try {
            if (!handleAnswer(deserialized))
                if (deserialized instanceof MeinRequest) {
                    MeinRequest request = (MeinRequest) deserialized;
                    try {
                        this.partnerCertificate = meinAuthSocket.getMeinAuthService().getCertificateManager().getTrustedCertificateByUuid(request.getUserUuid());
                        assert partnerCertificate != null;
                        this.decryptedSecret = meinAuthSocket.getMeinAuthService().getCertificateManager().decrypt(request.getSecret());
                        this.mySecret = UUID.randomUUID().toString();
                        IsolationDetails isolationDetails = null;
                        if (request.getPayload() != null && request.getPayload() instanceof IsolationDetails) {
                            isolationDetails = (IsolationDetails) request.getPayload();
                        }
                        byte[] secret = Cryptor.encrypt(partnerCertificate, mySecret);
                        MeinRequest answer = request.request().setRequestHandler(this).queue()
                                .setDecryptedSecret(decryptedSecret)
                                .setSecret(secret);
                        IsolationDetails finalIsolationDetails = isolationDetails;
                        answer.getAnswerDeferred().done(result -> {
                            MeinRequest r = (MeinRequest) result;
                            MeinResponse response = r.reponse();
                            try {
                                if (r.getDecryptedSecret().equals(mySecret)) {
                                    if (finalIsolationDetails == null) {
                                        //todo debug
                                        partnerAuthenticated = true;
                                        // get all allowed Services
                                        MeinAuthProcess.addAllowedServices(meinAuthSocket.getMeinAuthService(), partnerCertificate, response);
                                        send(response);
                                        // done here, set up validationprocess
                                        Lok.debug(meinAuthSocket.getMeinAuthService().getName() + " AuthProcess leaves socket");
                                        MeinValidationProcess validationProcess = new MeinValidationProcess(socket, partnerCertificate);
                                        // tell MAS we are connected & authenticated
                                        meinAuthSocket.getMeinAuthService().onSocketAuthenticated(validationProcess);
                                        // propagate that we are connected!
                                        propagateAuthentication(this.partnerCertificate, socket.getSocket().getInetAddress().getHostAddress(), socket.getSocket().getPort());
                                    } else {
                                        Lok.debug("MeinAuthProcess.isolation.onMessageReceived");
                                        IMeinService service = meinAuthSocket.getMeinAuthService().getMeinService(finalIsolationDetails.getTargetService());
                                        Class<? extends MeinIsolatedProcess> isolatedClass = (Class<? extends MeinIsolatedProcess>) getClass().forName(finalIsolationDetails.getProcessClass());
                                        MeinIsolatedProcess isolatedProcess = MeinIsolatedProcess.instance(isolatedClass, meinAuthSocket, service, partnerCertificate.getId().v(), finalIsolationDetails.getSourceService(), finalIsolationDetails.getIsolationUuid());
                                        service.onIsolatedConnectionEstablished(isolatedProcess);
                                        send(response);
                                    }
                                } else {
                                    response.setState(MeinStrings.msg.STATE_ERR);
                                    send(response);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                MeinAuthProcess.this.removeThyself();
                            }
                        });
                        send(answer);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else
                    Lok.debug("MeinAuthProcess.onMessageReceived.ELSE1");
        } catch (Exception e) {
            try {
                socket.disconnect();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }


    private void propagateAuthentication(Certificate partnerCertificate, String address, int port) throws SqlQueriesException {
        // propagate to database first
        partnerCertificate.setAddress(address);
        partnerCertificate.setPort(port);
        meinAuthSocket.getMeinAuthService().getCertificateManager().updateCertificate(partnerCertificate);
        // propagate to allowed services
        List<Service> services = meinAuthSocket.getMeinAuthService().getDatabaseManager().getAllowedServices(partnerCertificate.getId().v());
        for (Service service : services) {
            IMeinService ins = meinAuthSocket.getMeinAuthService().getMeinService(service.getUuid().v());
            if (ins != null) {
                ins.connectionAuthenticated(partnerCertificate);
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + meinAuthSocket.getMeinAuthService().getName();
    }

    void authenticate(AConnectJob job) {
        final Long id = job.getCertificateId();
        final String address = job.getAddress();
        final Integer port = job.getPort();
//        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        try {
            meinAuthSocket.connectSSL(id, address, port);
            mySecret = UUID.randomUUID().toString();
            if (partnerCertificate == null)
                this.partnerCertificate = meinAuthSocket.getTrustedPartnerCertificate();
            N runner = new N(e -> {
                e.printStackTrace();
                job.getPromise().reject(e);
            });
            this.mySecret = UUID.randomUUID().toString();
            byte[] secret = Cryptor.encrypt(partnerCertificate, mySecret);
            MeinRequest request = new MeinRequest(MeinStrings.SERVICE_NAME, MeinStrings.msg.INTENT_AUTH)
                    .setRequestHandler(this).queue().setSecret(secret)
                    .setUserUuid(partnerCertificate.getAnswerUuid().v());
            if (job instanceof IsolatedConnectJob) {
                IsolatedConnectJob isolatedConnectJob = (IsolatedConnectJob) job;
                IsolationDetails isolationDetails = new IsolationDetails()
                        .setTargetService(isolatedConnectJob.getRemoteServiceUuid())
                        .setSourceService(isolatedConnectJob.getOwnServiceUuid())
                        .setIsolationUuid(((IsolatedConnectJob) job).getIsolatedUuid())
                        .setProcessClass(((IsolatedConnectJob) job).getProcessClass().getCanonicalName());
                request.setPayLoad(isolationDetails);
            }
            request.getAnswerDeferred().done(result -> {
                MeinRequest r = (MeinRequest) result;
                if (r.getDecryptedSecret().equals(this.mySecret)) {
                    runner.runTry(() -> {
                        decryptedSecret = meinAuthSocket.getMeinAuthService().getCertificateManager().decrypt(r.getSecret());
                        MeinRequest answer = r.request()
                                .setDecryptedSecret(decryptedSecret)
                                .setAuthenticated(true)
                                .setRequestHandler(this).queue();
                        answer.getAnswerDeferred().done(result1 -> {
                            runner.runTry(() -> {
                                if (job instanceof ConnectJob) {
                                    // propagate that we are connected!
                                    propagateAuthentication(partnerCertificate, meinAuthSocket.getSocket().getInetAddress().getHostAddress(), meinAuthSocket.getSocket().getPort());
                                    // done here, set up validationprocess
                                    Lok.debug(meinAuthSocket.getMeinAuthService().getName() + " AuthProcess leaves socket");
                                    MeinValidationProcess validationProcess = new MeinValidationProcess(meinAuthSocket, partnerCertificate);
                                    // tell MAS we are connected & authenticated
                                    meinAuthSocket.getMeinAuthService().onSocketAuthenticated(validationProcess);
                                    job.getPromise().resolve(validationProcess);
                                    //
                                    final Long[] actualRemoteCertId = new Long[1];
                                    runner.runTry(() -> {
                                        actualRemoteCertId[0] = (job.getCertificateId() == null) ? partnerCertificate.getId().v() : job.getCertificateId();
                                        meinAuthSocket.getMeinAuthService().updateCertAddresses(actualRemoteCertId[0], address, port, job.getPortCert());
                                    });
                                } else if (job instanceof IsolatedConnectJob) {
                                    IsolatedConnectJob isolatedConnectJob = (IsolatedConnectJob) job;
                                    if (partnerCertificate.getId().v() != job.getCertificateId()) {
                                        job.getPromise().reject(new Exception("not the partner I expected"));
                                    } else {
                                        Lok.debug("MeinAuthProcess.authenticate465");
                                        IMeinService service = meinAuthSocket.getMeinAuthService().getMeinService(isolatedConnectJob.getOwnServiceUuid());
                                        Class isolatedProcessClass = isolatedConnectJob.getProcessClass();
                                        MeinIsolatedProcess meinIsolatedProcess = MeinIsolatedProcess.instance(isolatedProcessClass, meinAuthSocket, service, partnerCertificate.getId().v(), isolatedConnectJob.getRemoteServiceUuid(), isolatedConnectJob.getIsolatedUuid());
                                        Promise<Void, Exception, Void> isolated = meinIsolatedProcess.sendIsolate();
                                        isolated.done(nil -> {
                                            service.onIsolatedConnectionEstablished(meinIsolatedProcess);
                                            meinIsolatedProcess.setService(service);
                                            job.getPromise().resolve(meinIsolatedProcess);
                                        }).fail(excc -> job.getPromise().reject(excc));
                                    }
                                }

                            });
                        });
                        send(answer);
                    });
                } else {
                    //error stuff
                    Lok.debug("MeinAuthProcess.authenticate.error.decrypted.secret: " + r.getDecryptedSecret());
                    Lok.debug("MeinAuthProcess.authenticate.error.should.be: " + mySecret);
                    job.getPromise().reject(new Exception("find aok39ka"));
                }
            });
            send(request);
        } catch (Exception e) {
            job.getPromise().reject(e);
        }
    }

    public static void addAllowedServicesJoinTypes(MeinAuthService meinAuthService, Certificate partnerCertificate, MeinResponse response) throws SqlQueriesException {
        MeinServicesPayload payload = new MeinServicesPayload();
        response.setPayLoad(payload);
        List<ServiceJoinServiceType> servicesJoinTypes = meinAuthService.getDatabaseManager().getAllowedServicesJoinTypes(partnerCertificate.getId().v());
        //set flag for running Services, then add to result
        for (ServiceJoinServiceType service : servicesJoinTypes) {
            boolean running = meinAuthService.getMeinService(service.getUuid().v()) != null;
            service.setRunning(running);
            payload.addService(service);
        }
        response.setPayLoad(payload);
    }
}

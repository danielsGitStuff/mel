package de.mel.auth.socket;

import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelService;

import org.jdeferred.Promise;

import java.io.IOException;
import java.util.List;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.IsolationDetails;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.MelResponse;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.ConnectJob;
import de.mel.auth.jobs.IsolatedConnectJob;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.val.MelServicesPayload;
import de.mel.auth.tools.Cryptor;
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.sql.SqlQueriesException;

/**
 * handles authentication of incoming and outgoing connections
 * Created by xor on 4/21/16.
 */
@SuppressWarnings("Duplicates")
public class MelAuthProcess extends MelProcess {
    private String mySecret;
    // this is not mine
    private String decryptedSecret;
    private boolean partnerAuthenticated = false;
    private MelValidationProcess validationProcess;

    public MelAuthProcess(MelAuthSocket melAuthSocket) {
        super(melAuthSocket);
    }

    /**
     * adds the currently available services to the Response
     *
     * @param melAuthService
     * @param partnerCertificate
     * @param response
     * @throws SqlQueriesException
     */
    public static void addAllowedServices(MelAuthService melAuthService, Certificate partnerCertificate, MelResponse response) throws SqlQueriesException {
        MelServicesPayload payload = melAuthService.getAllowedServicesFor(partnerCertificate.getId().v());
        response.setPayLoad(payload);
    }

    @Override
    public void onMessageReceived(SerializableEntity deserialized, MelAuthSocket socket) {
        try {
            if (!handleAnswer(deserialized))
                if (deserialized instanceof MelRequest) {
                    MelRequest request = (MelRequest) deserialized;
                    try {
                        this.partnerCertificate = melAuthSocket.getMelAuthService().getCertificateManager().getTrustedCertificateByUuid(request.getUserUuid());
                        assert partnerCertificate != null;
                        this.decryptedSecret = melAuthSocket.getMelAuthService().getCertificateManager().decrypt(request.getSecret());
                        this.mySecret = CertificateManager.randomUUID().toString();
                        IsolationDetails isolationDetails = null;
                        if (request.getPayload() != null && request.getPayload() instanceof IsolationDetails) {
                            isolationDetails = (IsolationDetails) request.getPayload();
                        }
                        byte[] secret = Cryptor.encrypt(partnerCertificate, mySecret);
                        MelRequest answer = request.request().setRequestHandler(this).queue()
                                .setDecryptedSecret(decryptedSecret)
                                .setSecret(secret);
                        IsolationDetails finalIsolationDetails = isolationDetails;
                        answer.getAnswerDeferred().done(result -> {
                            MelRequest r = (MelRequest) result;
                            MelResponse response = r.reponse();
                            try {
                                if (r.getDecryptedSecret().equals(mySecret)) {
                                    if (finalIsolationDetails == null) {
                                        partnerAuthenticated = true;
                                        MelValidationProcess validationProcess = new MelValidationProcess(socket, partnerCertificate, true);
                                        if (melAuthSocket.getMelAuthService().registerValidationProcess(validationProcess)) {
                                            // get all allowed Services
                                            MelAuthProcess.addAllowedServices(melAuthSocket.getMelAuthService(), partnerCertificate, response);
                                            send(response);
                                            // done here, set up validationprocess
                                            Lok.debug(melAuthSocket.getMelAuthService().getName() + " AuthProcess leaves socket");
                                            // propagate that we are connected!
                                            // note: if the connection is incoming, we cannot use the ports from the socket here.
                                            // whenever an outgoing connection is set up a random high port is used to do so (port 50k+)
                                            propagateAuthentication(this.partnerCertificate, socket.getSocket().getInetAddress().getHostAddress(), partnerCertificate.getPort().v());
//                                            propagateAuthentication(this.partnerCertificate, socket.getSocket().getInetAddress().getHostAddress(), socket.getSocket().getLocalPort());
                                        } else {
//                                            Lok.debug("leaving, cause connection to cert " + partnerCertificate.getId().v() + " already exists. closing...");
                                            Lok.debug("connection to cert " + partnerCertificate.getId().v() + " already exists. waiting for the other side to close connection.");
//                                            this.stop();
                                        }

                                    } else {
                                        Lok.debug("leaving for IsolationProcess");
                                        IMelService service = melAuthSocket.getMelAuthService().getMelService(finalIsolationDetails.getTargetService());
                                        Class<? extends MelIsolatedProcess> isolatedClass = (Class<? extends MelIsolatedProcess>) getClass().forName(finalIsolationDetails.getProcessClass());
                                        MelIsolatedProcess isolatedProcess = MelIsolatedProcess.instance(isolatedClass, melAuthSocket, service, partnerCertificate.getId().v(), finalIsolationDetails.getSourceService(), finalIsolationDetails.getIsolationUuid());
                                        isolatedProcess.setService(service);
                                        service.onIsolatedConnectionEstablished(isolatedProcess);
                                        send(response);
                                    }
                                } else {
                                    response.setState(MelStrings.msg.STATE_ERR);
                                    send(response);
                                }
                            } catch (Exception e) {
                                Lok.error("leaving socket, because of EXCEPTION: " + e.toString());
                                MelAuthProcess.this.removeThyself();
                            }
                        });
                        send(answer);
                    } catch (Exception e) {
                        Lok.debug("leaving, because of exception: " + e.toString());
                        e.printStackTrace();
                    }
                } else
                    Lok.debug("MelAuthProcess.onMessageReceived.ELSE1");
        } catch (Exception e) {
            try {
                Lok.debug("leaving, because of exception: " + e.toString());
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
        melAuthSocket.getMelAuthService().getCertificateManager().updateCertificate(partnerCertificate);
        // propagate to allowed services
        List<Service> services = melAuthSocket.getMelAuthService().getDatabaseManager().getAllowedServices(partnerCertificate.getId().v());
        for (Service service : services) {
            IMelService ins = melAuthSocket.getMelAuthService().getMelService(service.getUuid().v());
            if (ins != null) {
                ins.connectionAuthenticated(partnerCertificate);
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + melAuthSocket.getMelAuthService().getName();
    }

    void authenticate(AConnectJob job) {
        final Long id = job.getCertificateId();
        final String address = job.getAddress();
        final Integer port = job.getPort();
        try {
            melAuthSocket.connectSSL(id, address, port);
            mySecret = CertificateManager.randomUUID().toString();
            if (partnerCertificate == null)
                this.partnerCertificate = melAuthSocket.getTrustedPartnerCertificate();
            N runner = new N(e -> {
                e.printStackTrace();
                job.reject(e);
            });
            this.mySecret = CertificateManager.randomUUID().toString();
            byte[] secret = Cryptor.encrypt(partnerCertificate, mySecret);
            MelRequest request = new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_AUTH)
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
                MelRequest r = (MelRequest) result;
                if (r.getDecryptedSecret().equals(this.mySecret)) {
                    runner.runTry(() -> {
                        decryptedSecret = melAuthSocket.getMelAuthService().getCertificateManager().decrypt(r.getSecret());
                        MelRequest answer = r.request()
                                .setDecryptedSecret(decryptedSecret)
                                .setAuthenticated(true)
                                .setRequestHandler(this).queue();
                        answer.getAnswerDeferred().done(result1 -> {
                            runner.runTry(() -> {
                                if (job instanceof ConnectJob) {
                                    // check if already connected to that cert
                                    MelValidationProcess validationProcess = new MelValidationProcess(melAuthSocket, partnerCertificate, false);
                                    if (melAuthSocket.getMelAuthService().registerValidationProcess(validationProcess)) {
                                        // propagate that we are connected!
                                        propagateAuthentication(partnerCertificate, melAuthSocket.getSocket().getInetAddress().getHostAddress(), melAuthSocket.getSocket().getPort());
                                        // done here, set up validationprocess
                                        Lok.debug(melAuthSocket.getMelAuthService().getName() + " AuthProcess leaves socket");
                                        // tell MAS we are connected & authenticated
                                        job.resolve(validationProcess);
                                        //
                                        final Long[] actualRemoteCertId = new Long[1];
                                        runner.runTry(() -> {
                                            actualRemoteCertId[0] = (job.getCertificateId() == null) ? partnerCertificate.getId().v() : job.getCertificateId();
                                            melAuthSocket.getMelAuthService().updateCertAddresses(actualRemoteCertId[0], address, port, job.getPortCert());
                                        });
                                    } else {
                                        Lok.debug("connection to cert " + partnerCertificate.getId().v() + " already existing. closing... v=" + melAuthSocket.getV());
                                        job.resolve(validationProcess);
                                        return;
                                    }


                                } else if (job instanceof IsolatedConnectJob) {
                                    IsolatedConnectJob isolatedConnectJob = (IsolatedConnectJob) job;
                                    if (partnerCertificate.getId().v() != job.getCertificateId()) {
                                        job.reject(new Exception("not the partner I expected"));
                                    } else {
                                        Lok.debug("MelAuthProcess.authenticate465");
                                        IMelService service = melAuthSocket.getMelAuthService().getMelService(isolatedConnectJob.getOwnServiceUuid());
                                        Class isolatedProcessClass = isolatedConnectJob.getProcessClass();
                                        MelIsolatedProcess melIsolatedProcess = MelIsolatedProcess.instance(isolatedProcessClass, melAuthSocket, service, partnerCertificate.getId().v(), isolatedConnectJob.getRemoteServiceUuid(), isolatedConnectJob.getIsolatedUuid());
                                        Promise<Void, Exception, Void> isolated = melIsolatedProcess.sendIsolate();
                                        isolated.done(nil -> {
                                            service.onIsolatedConnectionEstablished(melIsolatedProcess);
                                            melIsolatedProcess.setService(service);
                                            job.resolve(melIsolatedProcess);
                                        }).fail(excc -> job.reject(excc));
                                    }
                                }

                            });
                        });
                        send(answer);
                    });
                } else {
                    //error stuff
                    Lok.debug("MelAuthProcess.authenticate.error.decrypted.secret: " + r.getDecryptedSecret());
                    Lok.debug("MelAuthProcess.authenticate.error.should.be: " + mySecret);
                    job.reject(new Exception("find aok39ka"));
                }
            });
            send(request);
        } catch (Exception e) {
            Lok.error("Exception occured: " + e.toString() + " v=" + melAuthSocket.getV());
            job.reject(e);
        }
    }

    public static void addAllowedServicesJoinTypes(MelAuthService melAuthService, Certificate partnerCertificate, MelResponse response) throws SqlQueriesException {
        MelServicesPayload payload = new MelServicesPayload();
        response.setPayLoad(payload);
        List<ServiceJoinServiceType> servicesJoinTypes = melAuthService.getDatabaseManager().getAllowedServicesJoinTypes(partnerCertificate.getId().v());
        //set flag for running Services, then add to result
        for (ServiceJoinServiceType service : servicesJoinTypes) {
            MelService melService = melAuthService.getMelService(service.getUuid().v());
            service.setRunning(melService != null);
            if (melService != null) {
                service.setAdditionalServicePayload(melService.addAdditionalServiceInfo());
            }
            payload.addService(service);
        }
        response.setPayLoad(payload);
    }
}

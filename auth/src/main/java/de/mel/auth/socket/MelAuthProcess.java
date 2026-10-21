package de.mel.auth.socket;

import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelService;

import org.jdeferred.Promise;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

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
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.sql.SqlQueriesException;

/**
 * handles authentication of incoming and outgoing connections
 * Created by xor on 4/21/16.
 */
@SuppressWarnings("Duplicates")
public class MelAuthProcess extends MelProcess {
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

    /**
     * Server side. There is no application level handshake: TLS already made the peer prove that it owns the private key of the certificate it showed.
     * All that is left is to find out whether that certificate belongs to a device we are paired with.
     */
    @Override
    public void onMessageReceived(SerializableEntity deserialized, MelAuthSocket socket) {
        try {
            if (!handleAnswer(deserialized))
                if (deserialized instanceof MelRequest) {
                    MelRequest request = (MelRequest) deserialized;
                    try {
                        this.partnerCertificate = melAuthSocket.getTrustedPartnerCertificate();
                        if (partnerCertificate == null) {
                            Lok.debug("refusing: the peer does not show a certificate we trust");
                            refuse(request, socket, CloseReason.UNTRUSTED);
                            return;
                        }
                        IsolationDetails isolationDetails = null;
                        if (request.getPayload() != null && request.getPayload() instanceof IsolationDetails) {
                            isolationDetails = (IsolationDetails) request.getPayload();
                        }
                        MelResponse response = request.reponse();
                        if (isolationDetails == null) {
                            partnerAuthenticated = true;
                            MelValidationProcess validationProcess = new MelValidationProcess(socket, partnerCertificate, true);
                            if (melAuthSocket.getMelAuthService().registerValidationProcess(validationProcess)) {
                                socket.setState(new SocketState.Established(validationProcess, partnerCertificate));
                                // get all allowed Services
                                MelAuthProcess.addAllowedServices(melAuthSocket.getMelAuthService(), partnerCertificate, response);
                                send(response);
                                // done here, set up validationprocess
                                Lok.debug(melAuthSocket.getMelAuthService().getName() + " AuthProcess leaves socket");
                                // propagate that we are connected!
                                // note: if the connection is incoming, we cannot use the ports from the socket here.
                                // whenever an outgoing connection is set up a random high port is used to do so (port 50k+)
                                propagateAuthentication(this.partnerCertificate, socket.getSocket().getInetAddress().getHostAddress(), partnerCertificate.getPort().v());
                            } else {
                                Lok.debug("connection to cert " + partnerCertificate.getId().v() + " already exists and wins. closing this one.");
                                socket.duplicateLost();
                            }
                        } else {
                            Lok.debug("leaving for IsolationProcess");
                            if (!MelValidationProcess.isServiceAllowed(melAuthSocket.getMelAuthService(), partnerCertificate.getId().v(), isolationDetails.getTargetService())) {
                                Lok.debug("partner has no access to service " + isolationDetails.getTargetService());
                                refuse(request, socket, CloseReason.NO_ACCESS);
                                return;
                            }
                            IMelService service = melAuthSocket.getMelAuthService().getMelService(isolationDetails.getTargetService());
                            // the service decides which isolated processes it runs, the name from the partner is only a key
                            MelIsolatedProcess isolatedProcess = service.createIsolatedProcess(isolationDetails, melAuthSocket, partnerCertificate.getId().v());
                            if (isolatedProcess == null) {
                                Lok.debug("the service does not run an isolated process called " + isolationDetails.getProcessClass());
                                refuse(request, socket, CloseReason.NO_ACCESS);
                                return;
                            }
                            isolatedProcess.setService(service);
                            socket.setState(new SocketState.Isolated(isolatedProcess, partnerCertificate));
                            send(response);
                            // after the answer: the service may use the process at once, the partner has to know the connection is isolated by then
                            service.onIsolatedConnectionEstablished(isolatedProcess);
                        }
                    } catch (Exception e) {
                        Lok.error("leaving socket, because of EXCEPTION: " + e.toString());
                        e.printStackTrace();
                        refuse(request, socket, CloseReason.INTERNAL_ERROR);
                    }
                } else
                    Lok.debug("MelAuthProcess.onMessageReceived.ELSE1");
        } catch (Exception e) {
            Lok.debug("leaving, because of exception: " + e.toString());
            socket.close(CloseReason.INTERNAL_ERROR);
        }
    }

    /**
     * tells the peer that it is not welcome and hangs up
     */
    private void refuse(MelRequest request, MelAuthSocket socket, CloseReason reason) {
        N.r(() -> send(request.respondError(null)));
        socket.close(reason);
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

    /**
     * Client side. TLS has already authenticated both of us. Who answered is decided by the certificate it showed, not by whom we wanted to reach:
     * it has to be a trusted partner (otherwise we close), and if it is another trusted partner than the one we meant, the connection belongs to
     * that one (only its address and port are updated) and the job fails with {@link WrongPartnerException}.
     * The request only tells the server what we want, the response tells us whether it accepted us.
     */
    void authenticate(AConnectJob job) {
        final Long id = job.getCertificateId();
        final String address = job.getAddress();
        final Integer port = job.getPort();
        try {
            melAuthSocket.setState(new SocketState.Authenticating(this));
            melAuthSocket.connectSSL(id, address, port);
            this.partnerCertificate = melAuthSocket.getTrustedPartnerCertificate();
            if (partnerCertificate == null) {
                Lok.debug("closing: the one that answered shows a certificate we do not trust");
                reject(job, new UntrustedPartnerException("the one at " + address + ":" + port + " is no trusted partner"));
                melAuthSocket.close(CloseReason.UNTRUSTED);
                return;
            }
            final boolean expectedPartner = id == null || Objects.equals(partnerCertificate.getId().v(), id);
            if (!expectedPartner && job instanceof IsolatedConnectJob) {
                reject(job, new WrongPartnerException("expected certificate " + id + " but " + partnerCertificate.getId().v() + " answered"));
                melAuthSocket.close(CloseReason.WRONG_PARTNER);
                return;
            }
            N runner = new N(e -> {
                e.printStackTrace();
                reject(job, e);
            });
            MelRequest request = new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_AUTH)
                    .setRequestHandler(this).queue();
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
                runner.runTry(() -> {
                    if (job instanceof ConnectJob) {
                        MelValidationProcess validationProcess = new MelValidationProcess(melAuthSocket, partnerCertificate, false);
                        if (melAuthSocket.getMelAuthService().registerValidationProcess(validationProcess)) {
                            melAuthSocket.setState(new SocketState.Established(validationProcess, partnerCertificate));
                            // propagate that we are connected!
                            propagateAuthentication(partnerCertificate, melAuthSocket.getSocket().getInetAddress().getHostAddress(), melAuthSocket.getSocket().getPort());
                            Lok.debug(melAuthSocket.getMelAuthService().getName() + " AuthProcess leaves socket");
                            if (expectedPartner) {
                                // tell MAS we are connected & authenticated
                                job.resolve(validationProcess);
                                runner.runTry(() -> {
                                    Long actualRemoteCertId = (id == null) ? partnerCertificate.getId().v() : id;
                                    melAuthSocket.getMelAuthService().updateCertAddresses(actualRemoteCertId, address, port, job.getPortCert());
                                });
                            } else {
                                // the connection is kept, but it belongs to somebody else than the one that was asked for
                                reject(job, new WrongPartnerException("expected certificate " + id + " but " + partnerCertificate.getId().v() + " answered"));
                            }
                        } else {
                            Lok.debug("connection to cert " + partnerCertificate.getId().v() + " already exists and wins. closing this one. v=" + melAuthSocket.getV());
                            reject(job, new DuplicateConnectionException());
                            melAuthSocket.duplicateLost();
                        }
                    } else if (job instanceof IsolatedConnectJob) {
                        IsolatedConnectJob isolatedConnectJob = (IsolatedConnectJob) job;
                        Lok.debug("MelAuthProcess.authenticate465");
                        IMelService service = melAuthSocket.getMelAuthService().getMelService(isolatedConnectJob.getOwnServiceUuid());
                        Class isolatedProcessClass = isolatedConnectJob.getProcessClass();
                        MelIsolatedProcess melIsolatedProcess = MelIsolatedProcess.instance(isolatedProcessClass, melAuthSocket, service, partnerCertificate.getId().v(), isolatedConnectJob.getRemoteServiceUuid(), isolatedConnectJob.getIsolatedUuid());
                        melAuthSocket.setState(new SocketState.Isolated(melIsolatedProcess, partnerCertificate));
                        melIsolatedProcess.setService(service);
                        service.onIsolatedConnectionEstablished(melIsolatedProcess);
                        job.resolve(melIsolatedProcess);
                    }
                });
            }).fail(e -> {
                Exception reason = closeReason == CloseReason.DUPLICATE ? new DuplicateConnectionException() : e;
                reject(job, reason);
                if (!melAuthSocket.isStopped())
                    melAuthSocket.close(CloseReason.REFUSED);
            });
            send(request);
        } catch (Exception e) {
            Lok.error("Exception occured: " + e.toString() + " v=" + melAuthSocket.getV());
            reject(job, e);
            melAuthSocket.close(CloseReason.IO_ERROR);
        }
    }

    /**
     * A job is settled once. The socket reports its end to the job as well (ConnectedEnvironment), so whoever knows the real reason says it first.
     */
    @SuppressWarnings("unchecked")
    private static void reject(AConnectJob job, Exception reason) {
        if (!job.isPending())
            return;
        try {
            job.reject(reason);
        } catch (IllegalStateException e) {
            // settled in the meantime
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

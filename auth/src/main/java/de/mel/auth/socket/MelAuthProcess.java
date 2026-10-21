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
                            refuse(request, socket);
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
                                Lok.debug("connection to cert " + partnerCertificate.getId().v() + " already exists. closing this one.");
                                socket.close(CloseReason.DUPLICATE);
                            }
                        } else {
                            Lok.debug("leaving for IsolationProcess");
                            if (!MelValidationProcess.isServiceAllowed(melAuthSocket.getMelAuthService(), partnerCertificate.getId().v(), isolationDetails.getTargetService())) {
                                Lok.debug("partner has no access to service " + isolationDetails.getTargetService());
                                refuse(request, socket);
                                return;
                            }
                            IMelService service = melAuthSocket.getMelAuthService().getMelService(isolationDetails.getTargetService());
                            Class<? extends MelIsolatedProcess> isolatedClass = (Class<? extends MelIsolatedProcess>) getClass().forName(isolationDetails.getProcessClass());
                            MelIsolatedProcess isolatedProcess = MelIsolatedProcess.instance(isolatedClass, melAuthSocket, service, partnerCertificate.getId().v(), isolationDetails.getSourceService(), isolationDetails.getIsolationUuid());
                            isolatedProcess.setService(service);
                            socket.setState(new SocketState.Isolated(isolatedProcess, partnerCertificate));
                            service.onIsolatedConnectionEstablished(isolatedProcess);
                            send(response);
                        }
                    } catch (Exception e) {
                        Lok.error("leaving socket, because of EXCEPTION: " + e.toString());
                        e.printStackTrace();
                        refuse(request, socket);
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
    private void refuse(MelRequest request, MelAuthSocket socket) {
        N.r(() -> send(request.respondError(null)));
        socket.close(CloseReason.REFUSED);
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
     * Client side. TLS has already authenticated both of us: we trust the server's certificate (it is in our keystore since pairing)
     * and the server saw ours. The request only tells the server what we want, the response tells us whether it accepted us.
     */
    void authenticate(AConnectJob job) {
        final Long id = job.getCertificateId();
        final String address = job.getAddress();
        final Integer port = job.getPort();
        try {
            melAuthSocket.setState(new SocketState.Authenticating(this));
            melAuthSocket.connectSSL(id, address, port);
            if (partnerCertificate == null)
                this.partnerCertificate = melAuthSocket.getTrustedPartnerCertificate();
            N runner = new N(e -> {
                e.printStackTrace();
                job.reject(e);
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
                if (result instanceof MelResponse && MelStrings.msg.STATE_ERR.equals(((MelResponse) result).getState())) {
                    job.reject(new Exception("the server refused to authenticate us"));
                    return;
                }
                runner.runTry(() -> {
                    if (job instanceof ConnectJob) {
                        // check if already connected to that cert
                        MelValidationProcess validationProcess = new MelValidationProcess(melAuthSocket, partnerCertificate, false);
                        if (melAuthSocket.getMelAuthService().registerValidationProcess(validationProcess)) {
                            melAuthSocket.setState(new SocketState.Established(validationProcess, partnerCertificate));
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
                            melAuthSocket.close(CloseReason.DUPLICATE);
                            MelValidationProcess existing = melAuthSocket.getMelAuthService().getValidationProcess(partnerCertificate.getId().v());
                            if (existing != null)
                                job.resolve(existing);
                            else
                                job.reject(new Exception("duplicate connection"));
                            return;
                        }
                    } else if (job instanceof IsolatedConnectJob) {
                        IsolatedConnectJob isolatedConnectJob = (IsolatedConnectJob) job;
                        if (!Objects.equals(partnerCertificate.getId().v(), job.getCertificateId())) {
                            job.reject(new Exception("not the partner I expected"));
                        } else {
                            Lok.debug("MelAuthProcess.authenticate465");
                            IMelService service = melAuthSocket.getMelAuthService().getMelService(isolatedConnectJob.getOwnServiceUuid());
                            Class isolatedProcessClass = isolatedConnectJob.getProcessClass();
                            MelIsolatedProcess melIsolatedProcess = MelIsolatedProcess.instance(isolatedProcessClass, melAuthSocket, service, partnerCertificate.getId().v(), isolatedConnectJob.getRemoteServiceUuid(), isolatedConnectJob.getIsolatedUuid());
                            melAuthSocket.setState(new SocketState.Isolated(melIsolatedProcess, partnerCertificate));
                            Promise<Void, Exception, Void> isolated = melIsolatedProcess.sendIsolate();
                            isolated.done(nil -> {
                                service.onIsolatedConnectionEstablished(melIsolatedProcess);
                                melIsolatedProcess.setService(service);
                                job.resolve(melIsolatedProcess);
                            }).fail(excc -> job.reject(excc));
                        }
                    }
                });
            }).fail(e -> job.reject(e));
            send(request);
        } catch (Exception e) {
            Lok.error("Exception occured: " + e.toString() + " v=" + melAuthSocket.getV());
            melAuthSocket.close(CloseReason.IO_ERROR);
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

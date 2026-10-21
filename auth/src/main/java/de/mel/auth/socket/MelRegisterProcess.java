package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.*;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.socket.process.reg.IRegisteredHandler;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import org.jdeferred.impl.DeferredObject;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Handles a socket in the state REGISTERING, on both sides. C opened the socket, S accepted it.
 * <pre>
 * 1. C -> S  RegisterRequest (name, ports; the certificate is the TLS client certificate)
 * 2. S -> C  Decision(accepted), only if S's user decides first (informational)
 * 3. C -> S  Decision(accepted)
 * 4. S -> C  Final(accepted) once S has both decisions. S persists first, then sends it.
 * 5. C persists, then C closes the socket. S closes only on a timeout.
 * </pre>
 * A rejection ends it at once: <code>Decision(false)</code> (C) or <code>Final(false)</code> (S), nothing is persisted, the rejecting side closes.
 * Persisting is one statement that sets <code>trusted</code>, written only after the outcome is final, never during the wait for the users.
 * The decisions of the users arrive on other threads than the messages, so the state is guarded by a lock. Handlers are told outside of it.
 */
public class MelRegisterProcess extends MelProcess {
    private final CertificateManager certificateManager;
    private final Object lock = new Object();

    // guarded by lock
    private Certificate partner;
    private boolean wasTrustedBefore = false;
    private Boolean localDecision = null;
    private Boolean remoteDecision = null;
    private boolean remoteAcceptedReported = false;
    private boolean done = false;
    private MelRequest request;
    private X509Certificate peerX509;
    private RegisterDetails details;
    private DeferredObject<Certificate, Exception, Void> clientResult;
    private ScheduledFuture<?> timeoutTask;

    private void log(String msg) {
        Lok.debug("[" + Thread.currentThread().getName() + "] " + melAuthSocket.getMelAuthService().getName() + ".MelRegisterProcess: " + msg);
    }

    public MelRegisterProcess(MelAuthSocket melAuthSocket) {
        super(melAuthSocket);
        this.certificateManager = melAuthSocket.getMelAuthService().getCertificateManager();
    }

    private boolean isClient() {
        return melAuthSocket.getRole() == Transitions.Role.CLIENT;
    }

    private void tell(List<Runnable> afterwards, Consumer<IRegisterHandler> what) {
        afterwards.add(() -> {
            for (IRegisterHandler handler : new ArrayList<>(melAuthSocket.getMelAuthService().getRegisterHandlers()))
                what.accept(handler);
        });
    }

    private static void run(List<Runnable> afterwards) {
        for (Runnable runnable : afterwards) {
            try {
                runnable.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ---- client ----

    /**
     * Client side. The socket connects with the certificate that was imported before (untrusted, or trusted already when the partner is known).
     *
     * @param deferred resolved with the trusted entry when the registration is complete, rejected when it is not
     */
    public void register(DeferredObject<Certificate, Exception, Void> deferred, Long id, String address, int port) throws Exception {
        log("register id=" + id);
        MelAuthService service = melAuthSocket.getMelAuthService();
        List<Runnable> afterwards = new ArrayList<>();
        synchronized (lock) {
            clientResult = deferred;
            partner = certificateManager.getCertificateById(id);
            wasTrustedBefore = Boolean.TRUE.equals(partner.getTrusted().v());
            partner.setKnownPartner(wasTrustedBefore);
        }
        melAuthSocket.setState(new SocketState.Registering(this));
        melAuthSocket.connectSSL(id, address, port);
        synchronized (lock) {
            // whoever answered has to be the one whose certificate we hold
            if (!Arrays.equals(melAuthSocket.getPeerCertificate().getEncoded(), partner.getCertificate().v())) {
                failClient(afterwards, new UntrustedPartnerException("the one that answered is not the one whose certificate was fetched"), CloseReason.UNTRUSTED);
                run(afterwards);
                return;
            }
            startTimeout();
            RegisterDetails mine = new RegisterDetails(service.getName(), service.getSettings().getPort(), service.getSettings().getDeliveryPort());
            request = new MelRequest(MelStrings.SERVICE_NAME, MelStrings.msg.INTENT_REGISTER).setPayLoad(mine);
        }
        // the request leaves before the handlers are asked: a handler that decides within the call sends its decision behind it
        log("sending registration request");
        melAuthSocket.send(SerializableEntitySerializer.serialize(request));
        askHandlers(partner);
    }

    // ---- both ----

    private void askHandlers(Certificate about) throws Exception {
        MelAuthService service = melAuthSocket.getMelAuthService();
        IRegisterHandlerListener listener = new IRegisterHandlerListener() {
            @Override
            public void onCertificateAccepted(MelRequest request, Certificate certificate) {
                localDecided(true);
            }

            @Override
            public void onCertificateRejected(MelRequest request, Certificate certificate) {
                localDecided(false);
            }
        };
        if (service.getRegisterHandlers().isEmpty())
            Lok.debug("MelRegisterProcess: NO.HANDLER.FOR.REGISTRATION.AVAILABLE");
        for (IRegisterHandler handler : new ArrayList<>(service.getRegisterHandlers())) {
            log("asking register handler " + handler.getClass().getSimpleName());
            handler.acceptCertificate(listener, request, service.getMyCertificate(), about);
        }
    }

    private void startTimeout() {
        try {
            MelAuthService service = melAuthSocket.getMelAuthService();
            timeoutTask = service.getTimeoutScheduler().schedule(this::onTimeout, service.getRegisterTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // the instance is shut down
        }
    }

    private void onTimeout() {
        List<Runnable> afterwards = new ArrayList<>();
        synchronized (lock) {
            if (!done) {
                log("timeout");
                if (isClient())
                    failClient(afterwards, new Exception("registration timed out"), CloseReason.TIMEOUT);
                else
                    finishWithoutResult(CloseReason.TIMEOUT);
            } else {
                // nothing is left to do but closing: the server waits for the client to close, the client closes at once
                melAuthSocket.close(CloseReason.TIMEOUT);
            }
        }
        run(afterwards);
    }

    private void stopTimeout() {
        if (timeoutTask != null)
            timeoutTask.cancel(false);
    }

    /**
     * the user of this side decided (any thread)
     */
    private void localDecided(boolean accepted) {
        List<Runnable> afterwards = new ArrayList<>();
        synchronized (lock) {
            if (done || localDecision != null)
                return;
            localDecision = accepted;
            Certificate about = partner;
            log("local decision: " + accepted);
            if (!accepted) {
                tell(afterwards, handler -> handler.onLocallyRejected(about));
                if (isClient()) {
                    sendDecision(false);
                    failClient(afterwards, new UserDidNotTrustException(), CloseReason.REJECTED);
                } else {
                    sendFinal(false);
                    finishWithoutResult(CloseReason.REJECTED);
                }
            } else {
                tell(afterwards, handler -> handler.onLocallyAccepted(about));
                if (isClient()) {
                    sendDecision(true);
                } else if (Boolean.TRUE.equals(remoteDecision)) {
                    commitServer(afterwards);
                } else {
                    // informational, tells the client that it only waits for its own user
                    sendDecision(true);
                }
            }
        }
        run(afterwards);
    }

    private void sendDecision(boolean accepted) {
        send(new MelMessage(MelStrings.SERVICE_NAME, null).setPayLoad(new RegisterDecision(accepted)));
    }

    private void sendFinal(boolean accepted) {
        send(new MelMessage(MelStrings.SERVICE_NAME, null).setPayLoad(new RegisterFinal(accepted)));
    }

    private void send(MelMessage message) {
        try {
            melAuthSocket.send(SerializableEntitySerializer.serialize(message));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * the outcome is a rejection or the end of the connection and there is nothing to clean up (server side)
     */
    private void finishWithoutResult(CloseReason reason) {
        done = true;
        stopTimeout();
        melAuthSocket.close(reason);
    }

    /**
     * Client side, the registration failed. A certificate that was only fetched for it is removed again, one that was trusted before is not touched.
     */
    private void failClient(List<Runnable> afterwards, Exception reason, CloseReason closeReason) {
        done = true;
        stopTimeout();
        try {
            if (!wasTrustedBefore && partner != null)
                certificateManager.deleteCertificate(partner);
        } catch (Exception e) {
            e.printStackTrace();
        }
        DeferredObject<Certificate, Exception, Void> result = clientResult;
        if (result != null)
            afterwards.add(() -> {
                if (result.isPending())
                    result.reject(reason);
            });
        melAuthSocket.close(closeReason);
    }

    /**
     * Server side, both users accepted: persist, then say so. Persisting is one statement.
     */
    private void commitServer(List<Runnable> afterwards) {
        MelAuthService service = melAuthSocket.getMelAuthService();
        try {
            Certificate stored = certificateManager.registerTrusted(peerX509, details.getName(), melAuthSocket.getAddress(), details.getPort(), details.getDeliveryPort());
            done = true;
            sendFinal(true);
            reportCompleted(afterwards, stored);
        } catch (Exception e) {
            e.printStackTrace();
            sendFinal(false);
            finishWithoutResult(CloseReason.INTERNAL_ERROR);
        }
    }

    private void reportCompleted(List<Runnable> afterwards, Certificate stored) {
        MelAuthService service = melAuthSocket.getMelAuthService();
        tell(afterwards, handler -> handler.onRegistrationCompleted(stored));
        afterwards.add(() -> {
            for (IRegisteredHandler handler : new ArrayList<>(service.getRegisteredHandlers())) {
                try {
                    handler.onCertificateRegistered(service, stored);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // ---- what arrives ----

    @Override
    public void onMessageReceived(SerializableEntity deserialized, MelAuthSocket webSocket) {
        List<Runnable> afterwards = new ArrayList<>();
        boolean ask = false;
        synchronized (lock) {
            if (done)
                return;
            if (deserialized instanceof MelRequest) {
                if (isClient()) {
                    melAuthSocket.protocolViolation("a client does not receive registration requests");
                    return;
                }
                ask = onRegisterRequest((MelRequest) deserialized);
            } else if (deserialized instanceof MelMessage && ((MelMessage) deserialized).getPayload() instanceof RegisterDecision) {
                onDecision(((RegisterDecision) ((MelMessage) deserialized).getPayload()).isAccepted(), afterwards);
            } else if (deserialized instanceof MelMessage && ((MelMessage) deserialized).getPayload() instanceof RegisterFinal) {
                onFinal(((RegisterFinal) ((MelMessage) deserialized).getPayload()).isAccepted(), afterwards);
            } else {
                melAuthSocket.protocolViolation("unexpected " + deserialized.getClass().getSimpleName() + " while registering");
            }
        }
        run(afterwards);
        if (ask) {
            try {
                askHandlers(partner);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Server side. The partner is whoever owns the TLS client certificate, not what the request says.
     *
     * @return whether the handlers have to be asked
     */
    private boolean onRegisterRequest(MelRequest registerRequest) {
        log("received registration request");
        try {
            request = registerRequest;
            peerX509 = melAuthSocket.getPeerCertificate();
            if (!(registerRequest.getPayload() instanceof RegisterDetails)) {
                melAuthSocket.protocolViolation("registration request without details");
                return false;
            }
            details = (RegisterDetails) registerRequest.getPayload();
            Certificate known = certificateManager.getCertificateByHash(de.mel.sql.Hash.sha256(peerX509.getEncoded()));
            // for the handlers only, nothing is stored before both users said yes
            partner = new Certificate()
                    .setCertificate(peerX509.getEncoded())
                    .setName(details.getName())
                    .setAddress(melAuthSocket.getAddress())
                    .setPort(details.getPort())
                    .setCertDeliveryPort(details.getDeliveryPort())
                    .setTrusted(false)
                    .setKnownPartner(known != null);
            startTimeout();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            melAuthSocket.close(CloseReason.REFUSED);
            done = true;
            return false;
        }
    }

    private void onDecision(boolean accepted, List<Runnable> afterwards) {
        log("received decision, accepted=" + accepted);
        if (remoteDecision != null) {
            melAuthSocket.protocolViolation("decided twice");
            return;
        }
        remoteDecision = accepted;
        Certificate about = partner;
        if (!accepted) {
            tell(afterwards, handler -> handler.onRemoteRejected(about));
            if (isClient())
                failClient(afterwards, new PartnerDidNotTrustException(), CloseReason.REJECTED);
            else
                finishWithoutResult(CloseReason.REJECTED);
            return;
        }
        remoteAcceptedReported = true;
        tell(afterwards, handler -> handler.onRemoteAccepted(about));
        if (!isClient() && Boolean.TRUE.equals(localDecision))
            commitServer(afterwards);
    }

    /**
     * Client side
     */
    private void onFinal(boolean accepted, List<Runnable> afterwards) {
        log("received final, accepted=" + accepted);
        Certificate about = partner;
        if (!accepted) {
            if (localDecision == null || localDecision) // our own user did not say no, so this is news
                tell(afterwards, handler -> handler.onRemoteRejected(about));
            failClient(afterwards, new PartnerDidNotTrustException(), CloseReason.REJECTED);
            return;
        }
        if (!Boolean.TRUE.equals(localDecision)) {
            melAuthSocket.protocolViolation("the server finished a registration that this side did not accept");
            return;
        }
        try {
            // the commit: one statement
            certificateManager.trustCertificate(partner.getId().v(), true);
            Certificate stored = certificateManager.getCertificateById(partner.getId().v());
            done = true;
            stopTimeout();
            if (!remoteAcceptedReported)
                tell(afterwards, handler -> handler.onRemoteAccepted(about));
            reportCompleted(afterwards, stored);
            DeferredObject<Certificate, Exception, Void> result = clientResult;
            afterwards.add(() -> {
                if (result != null && result.isPending())
                    result.resolve(stored);
            });
            melAuthSocket.close(CloseReason.REGISTERED);
        } catch (Exception e) {
            e.printStackTrace();
            failClient(afterwards, e, CloseReason.INTERNAL_ERROR);
        }
    }

    @Override
    public void onSocketClosed(CloseReason reason, boolean remote) {
        List<Runnable> afterwards = new ArrayList<>();
        synchronized (lock) {
            if (!done) {
                log("socket closed before the registration was over: " + reason);
                Certificate about = partner;
                if (remote && about != null)
                    tell(afterwards, handler -> handler.onRemoteRejected(about));
                if (isClient())
                    failClient(afterwards, new Exception("connection closed: " + reason), reason);
                else {
                    done = true;
                    stopTimeout();
                }
            } else {
                stopTimeout();
            }
        }
        run(afterwards);
        super.onSocketClosed(reason, remote);
    }
}

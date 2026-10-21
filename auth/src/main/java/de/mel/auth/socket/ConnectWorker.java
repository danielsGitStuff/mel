package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.ConnectJob;
import de.mel.auth.jobs.IsolatedConnectJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.imprt.MelCertRetriever;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.net.ConnectException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Connects to another instance. One job, one straight sequence, every socket in a variable of its own:
 * <ol>
 *     <li>authenticate. This is tried first, also when the address is only known as an address: a partner that both sides trust already just connects</li>
 *     <li>only if that failed, nobody was named and registration is allowed: fetch the certificate (an entry that exists is reused), register on a socket of its own,
 *     wait until that socket is closed, authenticate on a new one</li>
 * </ol>
 * The job is settled once, with the outcome. Sockets that were handed over are not touched.
 */
public class ConnectWorker extends DeferredRunnable {
    private final AConnectJob connectJob;
    private final MelAuthService melAuthService;

    public ConnectWorker(MelAuthService melAuthService, AConnectJob connectJob) {
        this.melAuthService = melAuthService;
        this.connectJob = Objects.requireNonNull(connectJob);
    }

    @Override
    public String getRunnableName() {
        return "Connecting to: " + connectJob.getAddress() + ":" + connectJob.getPort() + "/" + connectJob.getPortCert();
    }

    @Override
    public void runImpl() {
        melAuthService.getPowerManager().wakeLock(this);
        try {
            if (connectJob instanceof ConnectJob)
                connect((ConnectJob) connectJob);
            else if (connectJob instanceof IsolatedConnectJob)
                isolate((IsolatedConnectJob<? extends MelIsolatedProcess>) connectJob);
        } catch (Throwable t) {
            t.printStackTrace();
            settle(t instanceof Exception ? (Exception) t : new Exception(t));
        } finally {
            melAuthService.getPowerManager().releaseWakeLock(this);
        }
    }

    @Override
    public DeferredObject<Void, Void, Void> onShutDown() {
        settle(new Exception("shutting down"));
        return DeferredRunnable.ResolvedDeferredObject();
    }

    @SuppressWarnings("unchecked")
    private void settle(Exception reason) {
        if (connectJob.isPending()) {
            try {
                connectJob.reject(reason);
            } catch (IllegalStateException e) {
                // settled in the meantime
            }
        }
    }

    /**
     * waits for a promise
     *
     * @throws Exception the reason if it was rejected
     */
    private <T> T await(Promise<T, ? extends Exception, ?> promise) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        promise.done(value -> {
            result.set(value);
            latch.countDown();
        }).fail(reason -> {
            failure.set(reason);
            latch.countDown();
        });
        // the steps have timeouts of their own, this is only to not wait for something that cannot come
        if (!latch.await(15, TimeUnit.MINUTES))
            throw new Exception("timed out");
        if (failure.get() != null)
            throw failure.get();
        return result.get();
    }

    private <T extends MelIsolatedProcess> void isolate(IsolatedConnectJob<T> job) {
        MelAuthSocket socket = new MelAuthSocket(melAuthService, job);
        socket.setRunnableName("-> " + job.getAddress() + ":" + job.getPort());
        new MelAuthProcess(socket).authenticate(job);
    }

    /**
     * one attempt to authenticate on a socket of its own
     */
    private DeferredObject<MelValidationProcess, Exception, Void> authenticate(Long certificateId) {
        ConnectJob attempt = new ConnectJob(certificateId, connectJob.getAddress(), connectJob.getPort(), connectJob.getPortCert(), false);
        MelAuthSocket socket = new MelAuthSocket(melAuthService, attempt);
        socket.setRunnableName("-> " + attempt.getAddress() + ":" + attempt.getPort());
        new MelAuthProcess(socket).authenticate(attempt);
        return attempt;
    }

    private void connect(ConnectJob job) {
        final Long remoteCertId = job.getCertificateId();
        final boolean mayRegister = job.getRegOnUnknown() && remoteCertId == null;
        MelValidationProcess process;
        try {
            process = await(authenticate(remoteCertId));
            // the one we meant to pair with is a trusted partner of both sides already
            process.setAlreadyPaired(mayRegister);
        } catch (Exception first) {
            if (first instanceof ShamefulSelfConnectException || first instanceof WrongPartnerException
                    || first instanceof DuplicateConnectionException || first instanceof ConnectException) {
                settle(first);
                return;
            }
            if (!mayRegister) {
                if (first instanceof UntrustedPartnerException)
                    settle(first);
                else
                    settle(new CannotConnectException(first, job.getAddress(), job.getPort()));
                return;
            }
            Lok.debug("could not authenticate (" + first + "), trying to register");
            try {
                process = registerAndAuthenticate(job);
            } catch (Exception e) {
                settle(e instanceof ShamefulSelfConnectException || e instanceof WrongPartnerException
                        || e instanceof DuplicateConnectionException ? e : new CannotConnectException(e, job.getAddress(), job.getPort()));
                return;
            }
        }
        try {
            job.resolve(process);
        } catch (IllegalStateException e) {
            // settled in the meantime, e.g. by a shutdown
        }
    }

    private MelValidationProcess registerAndAuthenticate(ConnectJob job) throws Exception {
        // 1. the certificate
        DeferredObject<Certificate, Exception, Object> fetched = new DeferredObject<>();
        new MelCertRetriever(melAuthService).retrieveCertificate(fetched, job.getAddress(), job.getPort(), job.getPortCert());
        Certificate certificate = await(fetched);
        job.setCertificateId(certificate.getId().v());
        // 2. register. The socket is closed by the registration, whatever its outcome.
        MelAuthSocket registerSocket = new MelAuthSocket(melAuthService);
        registerSocket.setRunnableName("register -> " + job.getAddress() + ":" + job.getPort());
        DeferredObject<Certificate, Exception, Void> registered = new DeferredObject<>();
        new MelRegisterProcess(registerSocket).register(registered, certificate.getId().v(), job.getAddress(), job.getPort());
        await(registered);
        // 3. wait until that socket is gone, then the partner is trusted on both sides and a new socket authenticates
        try {
            registerSocket.whenClosed().get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            registerSocket.close(CloseReason.TIMEOUT);
        }
        return await(authenticate(certificate.getId().v()));
    }
}

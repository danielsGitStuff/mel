package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.data.db.AddressCandidate;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.ConnectJob;
import de.mel.auth.jobs.IsolatedConnectJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.imprt.MelCertRetriever;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import fun.with.promise.AlreadyResolvedErrorException;
import fun.with.promise.AlreadyResolvedOkException;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import java.net.ConnectException;
import java.util.Collections;
import java.util.List;
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
    public Promise<Void, Void> onShutDown() {
        settle(new Exception("shutting down"));
        return DeferredRunnable.ResolvedDeferredObject();
    }

    @SuppressWarnings("unchecked")
    private void settle(Exception reason) {
        if (connectJob.isPending()) {
            try {
                connectJob.resolveError(reason);
            } catch (AlreadyResolvedOkException | AlreadyResolvedErrorException e) {
                // settled in the meantime
            }
        }
    }

    /**
     * waits for a promise
     *
     * @throws Exception the reason if it was rejected
     */
    private <T> T await(Promise<T, ? extends Exception> promise) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        promise.onDone(value -> {
            result.set(value);
            latch.countDown();
        }).onError(reason -> {
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
        List<AddressCandidate> candidates = job.getCandidates();
        if (candidates == null || candidates.size() <= 1) {
            MelAuthSocket socket = new MelAuthSocket(melAuthService, job);
            socket.setRunnableName("-> " + job.getAddress() + ":" + job.getPort());
            new MelAuthProcess(socket).authenticate(job);
            return;
        }
        Exception lastFailure = null;
        AddressCandidate lastCandidate = candidates.get(0);
        for (AddressCandidate candidate : candidates) {
            lastCandidate = candidate;
            IsolatedConnectJob<T> attempt = new IsolatedConnectJob<>(
                    job.getCertificateId(),
                    candidate.getAddress(),
                    candidate.getPort(),
                    candidate.getPortCert(),
                    job.getRemoteServiceUuid(),
                    job.getOwnServiceUuid(),
                    job.getProcessClass()
            );
            MelAuthSocket socket = new MelAuthSocket(melAuthService, attempt);
            socket.setRunnableName("-> " + attempt.getAddress() + ":" + attempt.getPort());
            new MelAuthProcess(socket).authenticate(attempt);
            try {
                T result = await(attempt);
                try {
                    job.resolveOk(result);
                } catch (AlreadyResolvedOkException | AlreadyResolvedErrorException ignored) {
                }
                return;
            } catch (Exception first) {
                if (isImmediateStop(first)) {
                    settle(first);
                    return;
                }
                lastFailure = first;
            }
        }
        settle(new CannotConnectException(lastFailure, lastCandidate.getAddress(), lastCandidate.getPort()));
    }

    private static boolean isImmediateStop(Exception e) {
        return e instanceof DuplicateConnectionException
                || e instanceof ShamefulSelfConnectException
                || e instanceof UntrustedPartnerException;
    }

    /**
     * one attempt to authenticate on a socket of its own
     */
    private PromiseImpl<MelValidationProcess, Exception> authenticate(Long certificateId, AddressCandidate candidate) {
        ConnectJob attempt = new ConnectJob(certificateId, candidate.getAddress(), candidate.getPort(), candidate.getPortCert(), false);
        MelAuthSocket socket = new MelAuthSocket(melAuthService, attempt);
        socket.setRunnableName("-> " + attempt.getAddress() + ":" + attempt.getPort());
        new MelAuthProcess(socket).authenticate(attempt);
        return attempt;
    }

    private PromiseImpl<MelValidationProcess, Exception> authenticate(Long certificateId) {
        return authenticate(certificateId, new AddressCandidate(connectJob.getAddress(), connectJob.getPort(), connectJob.getPortCert()));
    }

    private void connect(ConnectJob job) {
        final Long remoteCertId = job.getCertificateId();
        final boolean mayRegister = job.getRegOnUnknown() && remoteCertId == null;
        List<AddressCandidate> candidates = job.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            candidates = Collections.singletonList(new AddressCandidate(job.getAddress(), job.getPort(), job.getPortCert()));
        }

        MelValidationProcess process = null;
        if (mayRegister) {
            AddressCandidate primary = candidates.get(0);
            try {
                process = await(authenticate(remoteCertId, primary));
                // the one we meant to pair with is a trusted partner of both sides already
                process.setAlreadyPaired(true);
            } catch (Exception first) {
                if (first instanceof ShamefulSelfConnectException || first instanceof WrongPartnerException
                        || first instanceof DuplicateConnectionException || first instanceof ConnectException) {
                    settle(first);
                    return;
                }
                Lok.debug("could not authenticate (" + first + "), trying to register");
                try {
                    process = registerAndAuthenticate(job);
                } catch (Exception e) {
                    settle(e instanceof ShamefulSelfConnectException || e instanceof WrongPartnerException
                            || e instanceof DuplicateConnectionException ? e : new CannotConnectException(e, primary.getAddress(), primary.getPort()));
                    return;
                }
            }
        } else {
            Exception lastFailure = null;
            AddressCandidate lastCandidate = candidates.get(0);
            for (AddressCandidate candidate : candidates) {
                lastCandidate = candidate;
                try {
                    process = await(authenticate(remoteCertId, candidate));
                    process.setAlreadyPaired(false);
                    break;
                } catch (Exception first) {
                    if (isImmediateStop(first)) {
                        settle(first);
                        return;
                    }
                    lastFailure = first;
                }
            }
            if (process == null) {
                if (candidates.size() == 1 && (lastFailure instanceof WrongPartnerException || lastFailure instanceof ConnectException))
                    settle(lastFailure);
                else
                    settle(new CannotConnectException(lastFailure, lastCandidate.getAddress(), lastCandidate.getPort()));
                return;
            }
        }
        try {
            job.resolveOk(process);
        } catch (AlreadyResolvedOkException | AlreadyResolvedErrorException e) {
            // settled in the meantime, e.g. by a shutdown
        }
    }

    private MelValidationProcess registerAndAuthenticate(ConnectJob job) throws Exception {
        // 1. the certificate
        PromiseImpl<Certificate, Exception> fetched = PromiseImpl.withoutThrowingOnException();
        new MelCertRetriever(melAuthService).retrieveCertificate(fetched, job.getAddress(), job.getPort(), job.getPortCert());
        Certificate certificate = await(fetched);
        job.setCertificateId(certificate.getId().v());
        // 2. register. The socket is closed by the registration, whatever its outcome.
        MelAuthSocket registerSocket = new MelAuthSocket(melAuthService);
        registerSocket.setRunnableName("register -> " + job.getAddress() + ":" + job.getPort());
        PromiseImpl<Certificate, Exception> registered = PromiseImpl.withoutThrowingOnException();
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

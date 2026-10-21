package de.mel.auth.service;

import de.mel.auth.tools.lock2.BunchOfLocks;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import de.mel.Lok;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.ConnectJob;
import de.mel.auth.socket.ConnectWorker;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.DuplicateConnectionException;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.Transitions;
import de.mel.sql.Hash;
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock2.P;
import de.mel.sql.SqlQueriesException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xor on 13.10.2016.
 */
public class ConnectedEnvironment {
    private Map<Long, MelValidationProcess> idValidateProcessMap = new HashMap<>();
    private final Map<Long, List<PromiseImpl<MelValidationProcess, Exception>>> waitingForConnection = new HashMap<>();
    private String myHash;
    private Map<Long, AConnectJob> currentlyConnectingCertIds = new HashMap<>();
    private Map<String, AConnectJob> currentlyConnectingAddresses = new HashMap<>();

    private final MelAuthService melAuthService;

    ConnectedEnvironment(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
    }


    synchronized Promise<MelValidationProcess, Exception> connect(Certificate certificate) throws SqlQueriesException, InterruptedException {
        Lok.debug("connect to cert: " + certificate.getId().v() + " , addr: " + certificate.getAddress().v());
        PromiseImpl<MelValidationProcess, Exception> deferred = PromiseImpl.withoutThrowingOnException();
        long certificateId = certificate.getId().v();
        // check if already connected via id and address
        BunchOfLocks bunchOfLocks = null;
        try {
            bunchOfLocks = P.confine(P.read(this));
            AConnectJob def = isCurrentlyConnecting(certificateId);
            if (def != null) {
                return def;
            }
            {
                MelValidationProcess mvp = getValidationProcess(certificateId);
                if (mvp != null) {
                    return deferred.resolveOk(mvp);
                } else if ((mvp = getValidationProcess(certificate.getAddress().v(), certificate.getPort().v())) != null) {
                    return deferred.resolveOk(mvp);
                }
            }
            {
                ConnectJob job = new ConnectJob(certificateId, certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
                job.onDone(result -> {
                    // use a new transaction, because we want connect in parallel.
                    P.confine(this)
                            .run(() -> {
                                removeCurrentlyConnecting(certificateId);
                                removeCurrentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v());
                            })
                            .end();
                    deferred.resolveOk(result);
                }).onError(result -> {
                    P.confine(this)
                            .run(() -> {
                                removeCurrentlyConnecting(certificateId);
                                removeCurrentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v());
                            })
                            .end();
                    if (result instanceof DuplicateConnectionException)
                        whenConnected(certificateId, deferred, result);
                    else
                        deferred.resolveError(result);
                });
                currentlyConnecting(certificateId, job);
                currentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), job);
                melAuthService.execute(new ConnectWorker(melAuthService, job));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bunchOfLocks != null) {
                bunchOfLocks.end();
            }
        }
        return deferred;
    }

    private static AtomicInteger debug_count = new AtomicInteger(0);

    Promise<MelValidationProcess, Exception> connect(String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException {

        PromiseImpl<MelValidationProcess, Exception> deferred = PromiseImpl.withoutThrowingOnException();
        MelValidationProcess mvp;
        BunchOfLocks bunchOfLocks = null;
        // there are two try catch blocks because the connection code might be interrupted and needs to end the transaction under any circumstances
        try {
            bunchOfLocks = P.confine(this);
            Lok.debug("connect to: " + address + "," + port + "," + portCert + ",reg=" + regOnUnkown);
            AConnectJob def = isCurrentlyConnecting(address, port, portCert);
            if (def != null) {
                return def;
            }
            if ((mvp = getValidationProcess(address, port)) != null) {
                deferred.resolveOk(mvp);
            } else {
                ConnectJob job = new ConnectJob(null, address, port, portCert, regOnUnkown);
                job.onDone(result -> {
                    removeCurrentlyConnecting(address, port, portCert);
                    registerValidationProcess(result);
                    deferred.resolveOk(result);
                }).onError(result -> {
                    removeCurrentlyConnecting(address, port, portCert);
                    deferred.resolveError(result);
                });
                currentlyConnecting(address, port, portCert, job);
                melAuthService.execute(new ConnectWorker(melAuthService, job));
            }
        } finally {
            bunchOfLocks.end();
        }
        return deferred;
    }

    private String myHash() throws Exception {
        if (myHash == null)
            myHash = Hash.sha256(melAuthService.getCertificateManager().getMyX509Certificate().getEncoded());
        return myHash;
    }

    private String hashOf(Certificate certificate) throws Exception {
        if (certificate.getHash().v() != null)
            return certificate.getHash().v();
        return Hash.sha256(certificate.getCertificate().v());
    }

    /**
     * The hash of the certificate of the side that opened the connection. Both sides compute the same.
     */
    private String initiatorHash(MelValidationProcess process) throws Exception {
        if (process.getMelAuthSocket().getRole() == Transitions.Role.CLIENT)
            return myHash();
        return hashOf(process.getPartnerCertificate());
    }

    /**
     * Two normal connections to the same partner: exactly one survives, the same one on both sides.
     * Different initiators: the connection opened by the side with the lower certificate hash wins. Same initiator: the newer one wins
     * (the initiator gave the old one up, e.g. a half open TCP connection).
     */
    private boolean newConnectionWins(MelValidationProcess existing, MelValidationProcess fresh) throws Exception {
        String existingInitiator = initiatorHash(existing);
        String freshInitiator = initiatorHash(fresh);
        if (existingInitiator.equals(freshInitiator))
            return true;
        return freshInitiator.compareTo(existingInitiator) < 0;
    }

    /**
     * Decides, atomically, whether the connection is the one to keep for its partner. Has to be called when both the partner and the kind of
     * connection are known, before anything is answered. The loser (the new one or an existing one) is not closed here.
     *
     * @return true if {@link MelValidationProcess} has been registered as the only one connected with its {@link Certificate}.
     * If an existing connection lost, it is told so (and closes).
     */
    boolean registerValidationProcess(MelValidationProcess validationProcess) {
        if (validationProcess.isClosed())
            return false;
        MelValidationProcess loser = null;
        List<PromiseImpl<MelValidationProcess, Exception>> waiting = null;
        BunchOfLocks bunchOfLocks = P.confine(this);
        try {
            MelValidationProcess existingProcess = idValidateProcessMap.get(validationProcess.getConnectedId());
            if (existingProcess == validationProcess)
                return true;
            if (existingProcess != null && !existingProcess.isClosed()) {
                if (!newConnectionWins(existingProcess, validationProcess)) {
                    Lok.debug("an old connection is present for id " + validationProcess.getConnectedId() + " and wins");
                    return false;
                }
                loser = existingProcess;
            }
            idValidateProcessMap.put(validationProcess.getConnectedId(), validationProcess);
            waiting = waitingForConnection.remove(validationProcess.getConnectedId());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            bunchOfLocks.end();
        }
        if (loser != null) {
            Lok.debug("the new connection to id " + validationProcess.getConnectedId() + " wins, closing the old one");
            loser.getMelAuthSocket().duplicateLost();
        }
        if (waiting != null)
            for (PromiseImpl<MelValidationProcess, Exception> deferred : waiting)
                if (deferred.isPending())
                    deferred.resolveOk(validationProcess);
        return true;
    }

    /**
     * Passes on the connection to that partner as soon as there is one (a connect attempt lost against another connection that is about to be registered).
     * Fails if there is none after a while.
     */
    void whenConnected(Long certificateId, PromiseImpl<MelValidationProcess, Exception> deferred, Exception failure) {
        MelValidationProcess existing;
        BunchOfLocks bunchOfLocks = P.confine(this);
        try {
            existing = idValidateProcessMap.get(certificateId);
            if (existing == null || existing.isClosed()) {
                waitingForConnection.computeIfAbsent(certificateId, k -> new ArrayList<>()).add(deferred);
                try {
                    melAuthService.getTimeoutScheduler().schedule(() -> {
                        BunchOfLocks locks = P.confine(this);
                        try {
                            List<PromiseImpl<MelValidationProcess, Exception>> list = waitingForConnection.get(certificateId);
                            if (list != null)
                                list.remove(deferred);
                        } finally {
                            locks.end();
                        }
                        if (deferred.isPending())
                            deferred.resolveError(failure);
                    }, 10, TimeUnit.SECONDS);
                } catch (RuntimeException e) {
                    deferred.resolveError(failure);
                }
                return;
            }
        } finally {
            bunchOfLocks.end();
        }
        deferred.resolveOk(existing);
    }

    public Collection<MelValidationProcess> getValidationProcesses() {
        return idValidateProcessMap.values();
    }

    public MelValidationProcess getValidationProcess(Long certificateId) {
        MelValidationProcess process = idValidateProcessMap.get(certificateId);
        // one that is closed is gone, whether or not the bookkeeping caught up already
        return process == null || process.isClosed() ? null : process;
    }

    /**
     * The connection to the trusted partner that is known to live at that address, if there is one. The address is only a hint where to find
     * the partner, who it is is decided by its certificate.
     */
    public MelValidationProcess getValidationProcess(String address, int port) {
        try {
            for (Certificate certificate : melAuthService.getTrustedCertificates()) {
                if (address.equals(certificate.getAddress().v()) && certificate.getPort().v() != null && certificate.getPort().v() == port) {
                    MelValidationProcess process = idValidateProcessMap.get(certificate.getId().v());
                    if (process != null && !process.isClosed())
                        return process;
                }
            }
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Long> getConnectedIds() {
        List<Long> result = new ArrayList<>();
        for (MelValidationProcess mvp : idValidateProcessMap.values())
            result.add(mvp.getConnectedId());
        return result;
    }

    public void removeValidationProcess(MelAuthSocket melAuthSocket) {
        if (melAuthSocket.getProcess() == null || !(melAuthSocket.getProcess() instanceof MelValidationProcess))
            return;
        MelValidationProcess process = (MelValidationProcess) melAuthSocket.getProcess();
        if (idValidateProcessMap.get(process.getConnectedId()) == process)
            idValidateProcessMap.remove(process.getConnectedId());
        AConnectJob connectJob = melAuthSocket.getConnectJob();
        if (connectJob != null) {
            currentlyConnectingAddresses.values().removeIf(job -> job == connectJob);
            currentlyConnectingCertIds.values().removeIf(job -> job == connectJob);
        }
    }

    /**
     * checks whether or not you are currently connecting to that certificate
     *
     * @param certificateId
     * @return null if you don't
     */
    public AConnectJob isCurrentlyConnecting(Long certificateId) {
        return currentlyConnectingCertIds.get(certificateId);
    }

    private String uniqueAddress(String address, int port, int portCert) {
        return address + ";" + port + ";" + portCert;
    }

    /**
     * checks whether or not you are currently connecting to that address
     *
     * @param address
     * @param port
     * @param portCert
     * @return
     */
    public AConnectJob isCurrentlyConnecting(String address, int port, int portCert) {
        String id = uniqueAddress(address, port, portCert);
        return currentlyConnectingAddresses.get(id);
    }

    public void removeCurrentlyConnecting(Long certificateId) {
        currentlyConnectingCertIds.remove(certificateId);
    }

    public void removeCurrentlyConnecting(String address, int port, int portCert) {
        currentlyConnectingAddresses.remove(uniqueAddress(address, port, portCert));
    }

    /**
     * remembers that you are currently connecting to certificate
     *
     * @param certificateId
     * @param connectJob
     */
    public void currentlyConnecting(Long certificateId, AConnectJob connectJob) {
        currentlyConnectingCertIds.put(certificateId, connectJob);
    }

    /**
     * remembers that you are currently connecting to address
     *
     * @param address
     * @param port
     * @param portCert
     * @return
     */
    private void currentlyConnecting(String address, int port, int portCert, AConnectJob connectJob) {
        currentlyConnectingAddresses.put(uniqueAddress(address, port, portCert), connectJob);
    }

    public void shutDown() {
        Lok.debug("attempting shutdown");
        BunchOfLocks bunchOfLocks = P.confine(this);
        N.forEachAdvIgnorantly(currentlyConnectingAddresses, (stoppable, index, s, connectJob) -> {
            connectJob.resolveError(new Exception("shutting down"));
        });
        N.forEachAdvIgnorantly(currentlyConnectingCertIds, (stoppable, index, aLong, connectJob) -> {
            connectJob.resolveError(new Exception("shutting down"));
        });
        currentlyConnectingAddresses.clear();
        currentlyConnectingCertIds.clear();
        bunchOfLocks.end();
        Lok.debug("success");
    }

    public void onSocketClosed(MelAuthSocket melAuthSocket) {
        BunchOfLocks bunchOfLocks = null;
        try {
            bunchOfLocks = P.confine(this);
            // find the socket in the connected environment and remove it
            AConnectJob connectJob = melAuthSocket.getConnectJob();
            if (melAuthSocket.isValidated() && melAuthSocket.getProcess() instanceof MelValidationProcess) {
                removeValidationProcess(melAuthSocket);
            } else if (melAuthSocket.getProcess() instanceof MelIsolatedFileProcess) {
                Lok.debug("continue here");
            } else if (connectJob != null) {
                if (connectJob.getCertificateId() != null) {
                    N.r(() -> removeCurrentlyConnecting(melAuthSocket.getConnectJob().getCertificateId()));
                } else if (connectJob.getAddress() != null) {
                    N.r(() -> removeCurrentlyConnecting(connectJob.getAddress(), connectJob.getPort(), connectJob.getPortCert()));
                }
                bunchOfLocks.end();
                N.oneLine(() -> {
                    if (connectJob.isPending()) {
                        connectJob.resolveError(new Exception("connection closed"));
                    }
                });
            }
        } finally {

            if (bunchOfLocks != null) {
                bunchOfLocks.end();
            }
        }
    }
}

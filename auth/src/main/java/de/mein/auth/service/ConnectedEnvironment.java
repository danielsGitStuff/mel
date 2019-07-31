package de.mein.auth.service;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import de.mein.Lok;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.ConnectJob;
import de.mein.auth.socket.ConnectWorker;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.tools.N;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import de.mein.sql.SqlQueriesException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xor on 13.10.2016.
 */
public class ConnectedEnvironment {
    private Map<Long, MeinValidationProcess> idValidateProcessMap = new HashMap<>();
    private Map<String, MeinValidationProcess> addressValidateProcessMap = new HashMap<>();
    private Map<Long, MeinAuthSocket> currentlyConnectingCertIds = new HashMap<>();
    private Map<String, MeinAuthSocket> currentlyConnectingAddresses = new HashMap<>();

    private final MeinAuthService meinAuthService;

    ConnectedEnvironment(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }


    synchronized Promise<MeinValidationProcess, Exception, Void> connect(Certificate certificate) throws SqlQueriesException, InterruptedException {
        Lok.debug("connect to cert: " + certificate.getId().v() + " , addr: " + certificate.getAddress().v());
        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        long certificateId = certificate.getId().v();
        // check if already connected via id and address
        Transaction transaction = null;
        try {
            transaction = T.lockingTransaction(T.read(this));
            MeinAuthSocket def = isCurrentlyConnecting(certificateId);
            if (def != null) {
                return def.getConnectJob().getPromise();
            }
            {
                MeinValidationProcess mvp = getValidationProcess(certificateId);
                if (mvp != null) {
                    return deferred.resolve(mvp);
                } else if ((mvp = getValidationProcess(certificate.getAddress().v(), certificate.getPort().v())) != null) {
                    return deferred.resolve(mvp);
                }
            }
            {
                ConnectJob job = new ConnectJob(certificateId, certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
                job.getPromise().done(result -> {
                    // use a new transaction, because we want connect in parallel.
                    T.lockingTransaction(this)
                            .run(() -> {
                                removeCurrentlyConnecting(certificateId);
                                removeCurrentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v());
                            })
                            .end();
                    deferred.resolve(result);
                }).fail(result -> {
                    T.lockingTransaction(this)
                            .run(() -> {
                                removeCurrentlyConnecting(certificateId);
                                removeCurrentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v());
                            })
                            .end();
                    deferred.reject(result);
                });
                MeinAuthSocket meinAuthSocket = new MeinAuthSocket(meinAuthService, job);
                currentlyConnecting(certificateId, meinAuthSocket);
                currentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), meinAuthSocket);
                meinAuthService.execute(new ConnectWorker(meinAuthService, job));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (transaction != null) {
                transaction.end();
            }
        }
        return deferred;
    }

    private static AtomicInteger debug_count = new AtomicInteger(0);

    Promise<MeinValidationProcess, Exception, Void> connect(String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException {

        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        MeinValidationProcess mvp;
        Transaction transaction = null;
        // there are two try catch blocks because the connection code might be interrupted and needs to end the transaction under any circumstances
        try {
            transaction = T.lockingTransaction(this);
            Lok.debug("connect to: " + address + "," + port + "," + portCert + ",reg=" + regOnUnkown);
            MeinAuthSocket def = isCurrentlyConnecting(address, port, portCert);
            if (def != null) {
                return def.getConnectJob().getPromise();
            }
            if ((mvp = getValidationProcess(address, port)) != null) {
                deferred.resolve(mvp);
            } else {
                ConnectJob job = new ConnectJob(null, address, port, portCert, regOnUnkown);
                job.getPromise().done(result -> {
                    removeCurrentlyConnecting(address, port, portCert);
                    registerValidationProcess(result);
                    deferred.resolve(result);
                }).fail(result -> {
                    removeCurrentlyConnecting(address, port, portCert);
                    deferred.reject(result);
                });
                MeinAuthSocket meinAuthSocket = new MeinAuthSocket(meinAuthService, job);
                currentlyConnecting(address, port, portCert, meinAuthSocket);

                meinAuthService.execute(new ConnectWorker(meinAuthService, meinAuthSocket));
            }
        } finally {
            transaction.end();
        }
        return deferred;
    }

    /**
     * @param validationProcess
     * @return true if {@link MeinValidationProcess} has been registered as the only one connected with its {@link Certificate}
     */
    boolean registerValidationProcess(MeinValidationProcess validationProcess) {
        if (validationProcess.isClosed())
            return false;
        Transaction transaction = T.lockingTransaction(this);
        try {
            MeinValidationProcess existingProcess = idValidateProcessMap.get(validationProcess.getConnectedId());
            if (existingProcess != null) {
                if (existingProcess.isClosed())
                    Lok.error("an old socket was closed and somehow was not thrown away!");
                else
                    Lok.error("an old socket is already present for id: " + validationProcess.getConnectedId());
                return false;
            }
            idValidateProcessMap.put(validationProcess.getConnectedId(), validationProcess);
            addressValidateProcessMap.put(validationProcess.getAddressString(), validationProcess);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            transaction.end();
        }
    }

    public Collection<MeinValidationProcess> getValidationProcesses() {
        return idValidateProcessMap.values();
    }

    public MeinValidationProcess getValidationProcess(Long certificateId) {
        return idValidateProcessMap.get(certificateId);
    }

    public MeinValidationProcess getValidationProcess(String address, int port) {
        String completeAddress = address + ":" + port;
        return addressValidateProcessMap.get(completeAddress);
    }

    public List<Long> getConnectedIds() {
        List<Long> result = new ArrayList<>();
        for (MeinValidationProcess mvp : idValidateProcessMap.values())
            result.add(mvp.getConnectedId());
        return result;
    }

    public void removeValidationProcess(MeinAuthSocket meinAuthSocket) {
        if (meinAuthSocket.getProcess() == null || !(meinAuthSocket.getProcess() instanceof MeinValidationProcess))
            return;
        MeinValidationProcess process = (MeinValidationProcess) meinAuthSocket.getProcess();
        if (addressValidateProcessMap.get(process.getAddressString()) == process)
            addressValidateProcessMap.remove(process.getAddressString());
        if (idValidateProcessMap.get(process.getConnectedId()) == process)
            idValidateProcessMap.remove(process.getConnectedId());
        if (currentlyConnectingAddresses.containsKey(process.getAddressString())
                && currentlyConnectingAddresses.get(process.getAddressString()) == meinAuthSocket)
            currentlyConnectingAddresses.remove(meinAuthSocket);
        if (currentlyConnectingCertIds.containsKey(process.getConnectedId())
                && currentlyConnectingCertIds.get(meinAuthSocket) == meinAuthSocket)
            currentlyConnectingCertIds.remove(process.getConnectedId());
    }

    /**
     * checks whether or not you are currently connecting to that certificate
     *
     * @param certificateId
     * @return null if you don't
     */
    public MeinAuthSocket isCurrentlyConnecting(Long certificateId) {
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
    public MeinAuthSocket isCurrentlyConnecting(String address, int port, int portCert) {
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
     * @param meinAuthSocket
     */
    public void currentlyConnecting(Long certificateId, MeinAuthSocket meinAuthSocket) {
        currentlyConnectingCertIds.put(certificateId, meinAuthSocket);
    }

    /**
     * remembers that you are currently connecting to address
     *
     * @param address
     * @param port
     * @param portCert
     * @return
     */
    private void currentlyConnecting(String address, int port, int portCert, MeinAuthSocket meinAuthSocket) {
        currentlyConnectingAddresses.put(uniqueAddress(address, port, portCert), meinAuthSocket);
    }

    public void shutDown() {
        Lok.debug("attempting shutdown");
        Transaction transaction = T.lockingTransaction(this);
        N.forEachAdvIgnorantly(currentlyConnectingAddresses, (stoppable, index, s, meinAuthSocket) -> {
            if (meinAuthSocket.getConnectJob() != null)
                meinAuthSocket.getConnectJob().getPromise().reject(new Exception("shutting down"));
        });
        N.forEachAdvIgnorantly(currentlyConnectingCertIds, (stoppable, index, aLong, meinAuthSocket) -> {
            if (meinAuthSocket.getConnectJob() != null)
                meinAuthSocket.getConnectJob().getPromise().reject(new Exception("shutting down"));
        });
        currentlyConnectingAddresses.clear();
        currentlyConnectingCertIds.clear();
        transaction.end();
        Lok.debug("success");
    }

    public void onSocketClosed(MeinAuthSocket meinAuthSocket) {
        Transaction transaction = null;
        try {
            transaction = T.lockingTransaction(this);
            // find the socket in the connected environment and remove it
            AConnectJob connectJob = meinAuthSocket.getConnectJob();
            if (meinAuthSocket.isValidated() && meinAuthSocket.getProcess() instanceof MeinValidationProcess) {
                removeValidationProcess(meinAuthSocket);
            } else if (meinAuthSocket.getProcess() instanceof MeinIsolatedFileProcess) {
                Lok.debug("continue here");
            } else if (connectJob != null) {
                if (connectJob.getCertificateId() != null) {
                    N.r(() -> removeCurrentlyConnecting(meinAuthSocket.getConnectJob().getCertificateId()));
                } else if (connectJob.getAddress() != null) {
                    N.r(() -> removeCurrentlyConnecting(connectJob.getAddress(), connectJob.getPort(), connectJob.getPortCert()));
                }
                transaction.end();
                N.oneLine(() -> {
                    if (connectJob.getPromise().isPending()) {
                        connectJob.getPromise().reject(new Exception("connection closed"));
                    }
                });
            }
        } finally {

            if (transaction != null) {
                transaction.end();
            }
        }
    }
}

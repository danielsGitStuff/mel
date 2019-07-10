package de.mein.auth.service;

import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import de.mein.Lok;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.ConnectJob;
import de.mein.auth.socket.ConnectWorker;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import de.mein.sql.SqlQueriesException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 13.10.2016.
 */
public class ConnectedEnvironment {
    private Map<Long, MeinValidationProcess> idValidateProcessMap = new HashMap<>();
    private Map<String, MeinValidationProcess> addressValidateProcessMap = new HashMap<>();
    private Map<Long, Deferred<MeinValidationProcess, Exception, Void>> currentlyConnectingCertIds = new HashMap<>();
    private Map<String, Deferred<MeinValidationProcess, Exception, Void>> currentlyConnectingAddresses = new HashMap<>();

    private final MeinAuthService meinAuthService;

    ConnectedEnvironment(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }


    synchronized Promise<MeinValidationProcess, Exception, Void> connect(Certificate certificate) throws SqlQueriesException, InterruptedException {
        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        long certificateId = certificate.getId().v();
        // check if already connected via id and address
        Transaction transaction = null;
        try {
            transaction = T.lockingTransaction(T.read(this));
            Promise<MeinValidationProcess, Exception, Void> def = currentlyConnecting(certificateId);
            if (def != null) {
                return def;
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
                currentlyConnecting(certificateId, deferred);
                job.getPromise().done(result -> {
                    // use a new transaction, because we want connect in parallel.
                    T.lockingTransaction(this)
                            .run(() -> removeCurrentlyConnecting(certificateId))
                            .end();
                    deferred.resolve(result);
                }).fail(result -> {
                    T.lockingTransaction(this)
                            .run(() -> removeCurrentlyConnecting(certificateId))
                            .end();
                    deferred.reject(result);
                });
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

    Promise<MeinValidationProcess, Exception, Void> connect(String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException {
//        Lok.debug("connect: " + address + "," + port + "," + portCert + ",reg=" + regOnUnkown);
        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        MeinValidationProcess mvp;
        Transaction transaction = null;
        // there are two try catch blocks because the connection code might be interrupted and needs to end the transaction under any circumstances
        try {
            transaction = T.lockingTransaction(this);
        } finally {
            if (transaction != null) {
                try {
                    Promise<MeinValidationProcess, Exception, Void> def = currentlyConnecting(address, port, portCert);
                    if (def != null) {
                        transaction.end();
                        return def;
                    }
                    if ((mvp = getValidationProcess(address, port)) != null) {
                        deferred.resolve(mvp);
                    } else {
                        currentlyConnecting(address, port, portCert, deferred);
                        ConnectJob job = new ConnectJob(null, address, port, portCert, regOnUnkown);
                        job.getPromise().done(result -> {
                            removeCurrentlyConnecting(address, port, portCert);
                            registerValidationProcess(result);
                            deferred.resolve(result);
                        }).fail(result -> {
                            removeCurrentlyConnecting(address, port, portCert);
                            deferred.reject(result);
                        });
                        meinAuthService.execute(new ConnectWorker(meinAuthService, job));
                    }
                } finally {
                    transaction.end();
                }
            }
        }

        return deferred;
    }

//    private Semaphore semaphore = new Semaphore(1,true);

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

    public void removeValidationProcess(MeinValidationProcess process) {
//        N.r(() -> semaphore.acquire());
        addressValidateProcessMap.remove(process.getAddressString());
        idValidateProcessMap.remove(process.getConnectedId());
//        semaphore.release();
    }

    /**
     * checks whether or not you are currently connecting to that certificate
     *
     * @param certificateId
     * @return null if you don't
     */
    public Promise<MeinValidationProcess, Exception, Void> currentlyConnecting(Long certificateId) {
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
    public Promise<MeinValidationProcess, Exception, Void> currentlyConnecting(String address, int port, int portCert) {
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
     * @param deferred
     */
    public void currentlyConnecting(Long certificateId, DeferredObject<MeinValidationProcess, Exception, Void> deferred) {
        currentlyConnectingCertIds.put(certificateId, deferred);
    }

    /**
     * remembers that you are currently connecting to address
     *
     * @param address
     * @param port
     * @param portCert
     * @param deferred
     * @return
     */
    public void currentlyConnecting(String address, int port, int portCert, DeferredObject<MeinValidationProcess, Exception, Void> deferred) {
        currentlyConnectingAddresses.put(uniqueAddress(address, port, portCert), deferred);
    }

    public void shutDown() {
        Lok.debug("attempting shutdown");
        Transaction transaction = T.lockingTransaction(this);
        N.forEachAdvIgnorantly(currentlyConnectingAddresses, (stoppable, index, s, meinValidationProcessExceptionVoidDeferred) -> {
            meinValidationProcessExceptionVoidDeferred.reject(new Exception("shutting down"));
        });
        N.forEachAdvIgnorantly(currentlyConnectingCertIds, (stoppable, index, aLong, meinValidationProcessExceptionVoidDeferred) -> {
            meinValidationProcessExceptionVoidDeferred.reject(new Exception("shutting down"));
        });
        currentlyConnectingAddresses.clear();
        currentlyConnectingCertIds.clear();
        transaction.end();
        Lok.debug("success");
    }
}

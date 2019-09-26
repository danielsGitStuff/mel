package de.mel.auth.service;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import de.mel.Lok;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.ConnectJob;
import de.mel.auth.socket.ConnectWorker;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.sql.SqlQueriesException;

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
    private Map<Long, MelValidationProcess> idValidateProcessMap = new HashMap<>();
    private Map<String, MelValidationProcess> addressValidateProcessMap = new HashMap<>();
    private Map<Long, MelAuthSocket> currentlyConnectingCertIds = new HashMap<>();
    private Map<String, MelAuthSocket> currentlyConnectingAddresses = new HashMap<>();

    private final MelAuthService melAuthService;

    ConnectedEnvironment(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
    }


    synchronized Promise<MelValidationProcess, Exception, Void> connect(Certificate certificate) throws SqlQueriesException, InterruptedException {
        Lok.debug("connect to cert: " + certificate.getId().v() + " , addr: " + certificate.getAddress().v());
        DeferredObject<MelValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        long certificateId = certificate.getId().v();
        // check if already connected via id and address
        Warden warden = null;
        try {
            warden = P.confine(P.read(this));
            MelAuthSocket def = isCurrentlyConnecting(certificateId);
            if (def != null) {
                return def.getConnectJob();
            }
            {
                MelValidationProcess mvp = getValidationProcess(certificateId);
                if (mvp != null) {
                    return deferred.resolve(mvp);
                } else if ((mvp = getValidationProcess(certificate.getAddress().v(), certificate.getPort().v())) != null) {
                    return deferred.resolve(mvp);
                }
            }
            {
                ConnectJob job = new ConnectJob(certificateId, certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
                job.done(result -> {
                    // use a new transaction, because we want connect in parallel.
                    P.confine(this)
                            .run(() -> {
                                removeCurrentlyConnecting(certificateId);
                                removeCurrentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v());
                            })
                            .end();
                    deferred.resolve(result);
                }).fail(result -> {
                    P.confine(this)
                            .run(() -> {
                                removeCurrentlyConnecting(certificateId);
                                removeCurrentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v());
                            })
                            .end();
                    deferred.reject(result);
                });
                MelAuthSocket melAuthSocket = new MelAuthSocket(melAuthService, job);
                currentlyConnecting(certificateId, melAuthSocket);
                currentlyConnecting(certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), melAuthSocket);
                melAuthService.execute(new ConnectWorker(melAuthService, job));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (warden != null) {
                warden.end();
            }
        }
        return deferred;
    }

    private static AtomicInteger debug_count = new AtomicInteger(0);

    Promise<MelValidationProcess, Exception, Void> connect(String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException {

        DeferredObject<MelValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        MelValidationProcess mvp;
        Warden warden = null;
        // there are two try catch blocks because the connection code might be interrupted and needs to end the transaction under any circumstances
        try {
            warden = P.confine(this);
            Lok.debug("connect to: " + address + "," + port + "," + portCert + ",reg=" + regOnUnkown);
            MelAuthSocket def = isCurrentlyConnecting(address, port, portCert);
            if (def != null) {
                return def.getConnectJob();
            }
            if ((mvp = getValidationProcess(address, port)) != null) {
                deferred.resolve(mvp);
            } else {
                ConnectJob job = new ConnectJob(null, address, port, portCert, regOnUnkown);
                job.done(result -> {
                    removeCurrentlyConnecting(address, port, portCert);
                    registerValidationProcess(result);
                    deferred.resolve(result);
                }).fail(result -> {
                    removeCurrentlyConnecting(address, port, portCert);
                    deferred.reject(result);
                });
                MelAuthSocket melAuthSocket = new MelAuthSocket(melAuthService, job);
                currentlyConnecting(address, port, portCert, melAuthSocket);

                melAuthService.execute(new ConnectWorker(melAuthService, melAuthSocket));
            }
        } finally {
            warden.end();
        }
        return deferred;
    }

    /**
     * @param validationProcess
     * @return true if {@link MelValidationProcess} has been registered as the only one connected with its {@link Certificate}
     */
    boolean registerValidationProcess(MelValidationProcess validationProcess) {
        if (validationProcess.isClosed())
            return false;
        Warden warden = P.confine(this);
        try {
            MelValidationProcess existingProcess = idValidateProcessMap.get(validationProcess.getConnectedId());
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
            warden.end();
        }
    }

    public Collection<MelValidationProcess> getValidationProcesses() {
        return idValidateProcessMap.values();
    }

    public MelValidationProcess getValidationProcess(Long certificateId) {
        return idValidateProcessMap.get(certificateId);
    }

    public MelValidationProcess getValidationProcess(String address, int port) {
        String completeAddress = address + ":" + port;
        return addressValidateProcessMap.get(completeAddress);
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
        if (addressValidateProcessMap.get(process.getAddressString()) == process)
            addressValidateProcessMap.remove(process.getAddressString());
        if (idValidateProcessMap.get(process.getConnectedId()) == process)
            idValidateProcessMap.remove(process.getConnectedId());
        if (currentlyConnectingAddresses.containsKey(process.getAddressString())
                && currentlyConnectingAddresses.get(process.getAddressString()) == melAuthSocket)
            currentlyConnectingAddresses.remove(melAuthSocket);
        if (currentlyConnectingCertIds.containsKey(process.getConnectedId())
                && currentlyConnectingCertIds.get(melAuthSocket) == melAuthSocket)
            currentlyConnectingCertIds.remove(process.getConnectedId());
    }

    /**
     * checks whether or not you are currently connecting to that certificate
     *
     * @param certificateId
     * @return null if you don't
     */
    public MelAuthSocket isCurrentlyConnecting(Long certificateId) {
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
    public MelAuthSocket isCurrentlyConnecting(String address, int port, int portCert) {
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
     * @param melAuthSocket
     */
    public void currentlyConnecting(Long certificateId, MelAuthSocket melAuthSocket) {
        currentlyConnectingCertIds.put(certificateId, melAuthSocket);
    }

    /**
     * remembers that you are currently connecting to address
     *
     * @param address
     * @param port
     * @param portCert
     * @return
     */
    private void currentlyConnecting(String address, int port, int portCert, MelAuthSocket melAuthSocket) {
        currentlyConnectingAddresses.put(uniqueAddress(address, port, portCert), melAuthSocket);
    }

    public void shutDown() {
        Lok.debug("attempting shutdown");
        Warden warden = P.confine(this);
        N.forEachAdvIgnorantly(currentlyConnectingAddresses, (stoppable, index, s, melAuthSocket) -> {
            if (melAuthSocket.getConnectJob() != null)
                melAuthSocket.getConnectJob().reject(new Exception("shutting down"));
        });
        N.forEachAdvIgnorantly(currentlyConnectingCertIds, (stoppable, index, aLong, melAuthSocket) -> {
            if (melAuthSocket.getConnectJob() != null)
                melAuthSocket.getConnectJob().reject(new Exception("shutting down"));
        });
        currentlyConnectingAddresses.clear();
        currentlyConnectingCertIds.clear();
        warden.end();
        Lok.debug("success");
    }

    public void onSocketClosed(MelAuthSocket melAuthSocket) {
        Warden warden = null;
        try {
            warden = P.confine(this);
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
                warden.end();
                N.oneLine(() -> {
                    if (connectJob.isPending()) {
                        connectJob.reject(new Exception("connection closed"));
                    }
                });
            }
        } finally {

            if (warden != null) {
                warden.end();
            }
        }
    }
}

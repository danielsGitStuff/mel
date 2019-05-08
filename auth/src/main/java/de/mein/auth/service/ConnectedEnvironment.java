package de.mein.auth.service;

import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import de.mein.Lok;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 13.10.2016.
 */
public class ConnectedEnvironment extends WaitLock {
    private Map<Long, MeinValidationProcess> idValidateProcessMap = new HashMap<>();
    private Map<String, MeinValidationProcess> addressValidateProcessMap = new HashMap<>();
    private Map<Long, Deferred<MeinValidationProcess, Exception, Void>> currentlyConnectingCertIds = new HashMap<>();
    private Map<String, Deferred<MeinValidationProcess, Exception, Void>> currentlyConnectingAddresses = new HashMap<>();
//    private Semaphore semaphore = new Semaphore(1,true);

    public void addValidationProcess(MeinValidationProcess validationProcess) {
//        N.r(() -> semaphore.acquire());
        idValidateProcessMap.put(validationProcess.getConnectedId(), validationProcess);
        addressValidateProcessMap.put(validationProcess.getAddressString(), validationProcess);
//        semaphore.release();
    }

    public Collection<MeinValidationProcess> getValidationProcesses() {
        return idValidateProcessMap.values();
    }

    public MeinValidationProcess getValidationProcess(Long certificateId) {
        return idValidateProcessMap.get(certificateId);
    }

    public MeinValidationProcess getValidationProcess(String address) {
        return addressValidateProcessMap.get(address);
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

    @Override
    public synchronized WaitLock lock() {
        return super.lock();
    }

    @Override
    public WaitLock unlock() {
        return super.unlock();
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
        this.lock();
        N.forEachAdvIgnorantly(currentlyConnectingAddresses, (stoppable, index, s, meinValidationProcessExceptionVoidDeferred) -> {
            meinValidationProcessExceptionVoidDeferred.reject(new Exception("shutting down"));
        });
        N.forEachAdvIgnorantly(currentlyConnectingCertIds, (stoppable, index, aLong, meinValidationProcessExceptionVoidDeferred) -> {
            meinValidationProcessExceptionVoidDeferred.reject(new Exception("shutting down"));
        });
        currentlyConnectingAddresses.clear();
        currentlyConnectingCertIds.clear();
        this.unlock();
        Lok.debug("success");
    }
}

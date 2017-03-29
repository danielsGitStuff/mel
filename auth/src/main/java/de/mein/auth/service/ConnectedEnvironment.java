package de.mein.auth.service;

import de.mein.auth.socket.process.val.MeinValidationProcess;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by xor on 13.10.2016.
 */
public class ConnectedEnvironment {
    private Map<Long, MeinValidationProcess> idValidateProcessMap = new ConcurrentHashMap<>();
    private Map<String, MeinValidationProcess> addressValidateProcessMap = new ConcurrentHashMap<>();

    public synchronized void addValidationProcess(MeinValidationProcess validationProcess) {
        idValidateProcessMap.put(validationProcess.getConnectedId(), validationProcess);
        addressValidateProcessMap.put(validationProcess.getAddressString(), validationProcess);
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
        return idValidateProcessMap.values().stream().map(MeinValidationProcess::getConnectedId).collect(Collectors.toList());
    }

    public synchronized void removeValidationProcess(MeinValidationProcess process) {
        addressValidateProcessMap.remove(process.getAddressString());
        idValidateProcessMap.remove(process.getConnectedId());
    }
}

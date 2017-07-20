package de.mein.auth.service;

import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Created by xor on 13.10.2016.
 */
public class ConnectedEnvironment extends WaitLock{
    private Map<Long, MeinValidationProcess> idValidateProcessMap = new HashMap<>();
    private Map<String, MeinValidationProcess> addressValidateProcessMap = new HashMap<>();
    private Semaphore semaphore = new Semaphore(1,true);

    public void addValidationProcess(MeinValidationProcess validationProcess) {
        N.r(() -> semaphore.acquire());
        idValidateProcessMap.put(validationProcess.getConnectedId(), validationProcess);
        addressValidateProcessMap.put(validationProcess.getAddressString(), validationProcess);
        semaphore.release();
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

    public  void removeValidationProcess(MeinValidationProcess process) {
        N.r(() -> semaphore.acquire());
        addressValidateProcessMap.remove(process.getAddressString());
        idValidateProcessMap.remove(process.getConnectedId());
        semaphore.release();
    }
}

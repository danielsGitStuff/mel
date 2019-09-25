package de.mel.auth.jobs;

import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;

import java.util.UUID;

/**
 * Created by xor on 12/13/16.
 */
public class IsolatedConnectJob<T extends MelIsolatedProcess> extends AConnectJob<T, Void> {
    private final Class<T> processClass;
    private String remoteServiceUuid;
    private String ownServiceUuid;
    private String isolatedUuid;

    public IsolatedConnectJob(Long certificateId, String address, Integer port, Integer portCert, String remoteServiceUuid, String ownServiceUuid, Class<T> isolatedServiceClass) {
        super(certificateId, address, port, portCert);
        this.remoteServiceUuid = remoteServiceUuid;
        this.ownServiceUuid = ownServiceUuid;
        this.processClass = isolatedServiceClass;
        this.isolatedUuid = CertificateManager.randomUUID().toString();
    }

    public Class<T> getProcessClass() {
        return processClass;
    }

    public String getRemoteServiceUuid() {
        return remoteServiceUuid;
    }

    public String getOwnServiceUuid() {
        return ownServiceUuid;
    }


    public String getIsolatedUuid() {
        return isolatedUuid;
    }
}

package de.mel.auth.jobs;


import de.mel.Lok;
import de.mel.auth.tools.Eva;

/**
 * Created by xor on 12/13/16.
 */
public abstract class AConnectJob<R, P> extends Job<R, Exception, P> {
    private Long certificateId;
    private String address;
    private Integer port, portCert;

    public AConnectJob(Long certificateId, String address, Integer port, Integer portCert) {
        this.certificateId = certificateId;
        this.address = address;
        this.port = port;
        this.portCert = portCert;
    }

    public AConnectJob setCertificateId(Long certificateId) {
        this.certificateId = certificateId;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public Integer getPortCert() {
        return portCert;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public String getAddress() {
        return address;
    }

}

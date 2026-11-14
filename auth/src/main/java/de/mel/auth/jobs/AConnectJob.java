package de.mel.auth.jobs;


import de.mel.Lok;
import de.mel.auth.data.db.AddressCandidate;
import de.mel.auth.tools.Eva;

import java.util.List;

/**
 * Created by xor on 12/13/16.
 */
public abstract class AConnectJob<R> extends Job<R, Exception> {
    private Long certificateId;
    private String address;
    private Integer port, portCert;
    private List<AddressCandidate> candidates;

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

    public List<AddressCandidate> getCandidates() {
        return candidates;
    }

    public AConnectJob<R> setCandidates(List<AddressCandidate> candidates) {
        this.candidates = candidates;
        return this;
    }

}

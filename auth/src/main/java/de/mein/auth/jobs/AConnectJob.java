package de.mein.auth.jobs;

import sun.jvm.hotspot.StackTrace;

/**
 * Created by xor on 12/13/16.
 */
public abstract class AConnectJob<R, P> extends Job<R, Exception, P> {
    private final String codePosition;
    private Long certificateId;
    private String address;
    private Integer port, portCert;

    public AConnectJob(Long certificateId, String address, Integer port, Integer portCert) {
        this.certificateId = certificateId;
        this.address = address;
        this.port = port;
        this.portCert = portCert;
        StackTraceElement trace = Thread.currentThread().getStackTrace()[2];
        this.codePosition = trace.getClassName() + "." + trace.getMethodName() + "() line " + trace.getLineNumber();
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

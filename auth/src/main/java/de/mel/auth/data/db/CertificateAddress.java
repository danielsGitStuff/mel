package de.mel.auth.data.db;

import de.mel.core.serialize.SerializableEntity;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

public class CertificateAddress extends SQLTableObject implements SerializableEntity {
    public static final String ID = "id";
    public static final String CERTIFICATE_ID = "certificateid";
    public static final String ADDRESS = "address";
    public static final String PORT = "port";
    public static final String CERT_PORT = "certport";
    public static final String PRIORITY = "priority";

    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<Long> certificateId = new Pair<>(Long.class, CERTIFICATE_ID);
    private Pair<String> address = new Pair<>(String.class, ADDRESS);
    private Pair<Integer> port = new Pair<>(Integer.class, PORT);
    private Pair<Integer> certPort = new Pair<>(Integer.class, CERT_PORT);
    private Pair<Integer> priority = new Pair<>(Integer.class, PRIORITY);

    public CertificateAddress() {
        init();
    }

    @Override
    public String getTableName() {
        return "certificate_address";
    }

    @Override
    protected void init() {
        populateInsert(certificateId, address, port, certPort, priority);
        populateAll(id);
    }

    public Pair<Long> getId() {
        return id;
    }

    public CertificateAddress setId(Long id) {
        this.id.v(id);
        return this;
    }

    public Pair<Long> getCertificateId() {
        return certificateId;
    }

    public CertificateAddress setCertificateId(Long certificateId) {
        this.certificateId.v(certificateId);
        return this;
    }

    public Pair<String> getAddress() {
        return address;
    }

    public CertificateAddress setAddress(String address) {
        this.address.v(address);
        return this;
    }

    public Pair<Integer> getPort() {
        return port;
    }

    public CertificateAddress setPort(Integer port) {
        this.port.v(port);
        return this;
    }

    public Pair<Integer> getCertPort() {
        return certPort;
    }

    public CertificateAddress setCertPort(Integer certPort) {
        this.certPort.v(certPort);
        return this;
    }

    public Pair<Integer> getPriority() {
        return priority;
    }

    public CertificateAddress setPriority(Integer priority) {
        this.priority.v(priority);
        return this;
    }
}

package de.mein.auth.data.db;

import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.tools.Hash;
import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Created by xor on 3/16/16.
 */
public class Certificate extends SQLTableObject implements SerializableEntity {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> uuid = new Pair<>(String.class, "uuid");
    private Pair<String> answerUuid = new Pair<>(String.class, "answeruuid");
    private Pair<byte[]> certificate = new Pair<>(byte[].class, "certificate");
    private Pair<String> address = new Pair<>(String.class, "address");
    private Pair<Integer> port = new Pair<>(Integer.class, "port");
    private Pair<Integer> certDeliveryPort = new Pair<>(Integer.class, "certport");
    private Pair<String> name = new Pair<>(String.class, "name");
    private Pair<Boolean> trusted = new Pair<>(Boolean.class, "trusted");
    private Pair<String> greeting = new Pair<>(String.class, "greeting");

    @JsonIgnore
    private Pair<String> hash = new Pair<>(String.class, "hash");

    @Override
    public int hashCode() {
        int hash = (id.v() == null) ? super.hashCode() : id.v().hashCode();
        return hash;
    }

    public Certificate setGreeting(String greeting) {
        this.greeting.v(greeting);
        return this;
    }

    public Pair<String> getGreeting() {
        return greeting;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Certificate) {
            Certificate c = (Certificate) obj;
            boolean r = (c.id.v() == null && id.v() == null) || (c.id.v() == null) == (id.v() == null) || c.getId().v().equals(id.v());

            return r;
        }
        return false;
    }

    public Certificate() {
        init();
    }

    public PublicKey getPublicKey() throws CertificateException {
        byte[] certBytes = certificate.v();
        X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(certBytes);
        PublicKey publicKey = x509Certificate.getPublicKey();
        return publicKey;
    }

    @Override
    public String getTableName() {
        return "certificate";
    }

    @Override
    protected void init() {
        certificate.setSetListener(value -> {
            try {
                hash.v(Hash.sha256(value));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return value;
        });
        populateInsert(uuid, answerUuid, name, certificate, address, greeting, port, certDeliveryPort, trusted, hash);
        populateAll(id);
    }

    public Pair<Long> getId() {
        return id;
    }

    public Certificate setId(Long id) {
        this.id.v(id);
        return this;
    }

    public Pair<String> getHash() {
        return hash;
    }

    public Pair<Integer> getPort() {
        return port;
    }

    public Pair<Integer> getCertDeliveryPort() {
        return certDeliveryPort;
    }

    public Pair<String> getUuid() {
        return uuid;
    }

    public Certificate setUuid(String uuid) {
        this.uuid.v(uuid);
        return this;
    }

    public Pair<String> getAnswerUuid() {
        return answerUuid;
    }

    public Certificate setAnswerUuid(String answerUuid) {
        this.answerUuid.v(answerUuid);
        return this;
    }

    public Pair<byte[]> getCertificate() {
        return certificate;
    }

    public Certificate setCertificate(byte[] certificate) {
        this.certificate.v(certificate);
        return this;
    }

    public Pair<String> getAddress() {
        return address;
    }

    public InetAddress getInetAddress() throws UnknownHostException {
        return InetAddress.getByName(address.v());
    }

    public Certificate setAddress(String address) {
        this.address.v(address);
        return this;
    }

    public Pair<String> getName() {
        return name;
    }

    public Certificate setName(String name) {
        this.name.v(name);
        return this;
    }

    public X509Certificate getX509Certificate() throws CertificateException {
        X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(certificate.v());
        return x509Certificate;
    }

    public Certificate setPort(Integer port) {
        this.port.v(port);
        return this;
    }

    public Certificate setCertDeliveryPort(Integer certDeliveryPort) {
        this.certDeliveryPort.v(certDeliveryPort);
        return this;
    }

    public Pair<Boolean> getTrusted() {
        return trusted;
    }

    public Certificate setTrusted(Boolean trusted) {
        this.trusted.v(trusted);
        return this;
    }
}
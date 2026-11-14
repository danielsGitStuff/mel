package de.mel.auth.data.db;

public class AddressCandidate {
    private final String address;
    private final int port;
    private final int portCert;

    public AddressCandidate(String address, int port, int portCert) {
        this.address = address;
        this.port = port;
        this.portCert = portCert;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public int getPortCert() {
        return portCert;
    }
}

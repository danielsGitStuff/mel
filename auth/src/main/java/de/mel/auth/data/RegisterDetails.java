package de.mel.auth.data;

/**
 * What the client tells the server in a registration request. The certificate is not part of it: it is the TLS client certificate of the connection.
 */
public class RegisterDetails extends ServicePayload {
    private String name;
    private Integer port;
    private Integer deliveryPort;

    public RegisterDetails() {
    }

    public RegisterDetails(String name, Integer port, Integer deliveryPort) {
        this.name = name;
        this.port = port;
        this.deliveryPort = deliveryPort;
    }

    public String getName() {
        return name;
    }

    public Integer getPort() {
        return port;
    }

    public Integer getDeliveryPort() {
        return deliveryPort;
    }
}

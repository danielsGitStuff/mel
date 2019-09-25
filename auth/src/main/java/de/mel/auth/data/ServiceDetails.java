package de.mel.auth.data;

public class ServiceDetails extends ServicePayload {
    private String serviceUuid;

    public ServiceDetails() {

    }

    public ServiceDetails(String serviceUuid) {
        this(serviceUuid, null);
    }

    public ServiceDetails(String serviceUuid, String intent) {
        this.serviceUuid = serviceUuid;
        this.intent = intent;
    }

    public void setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }
}

package de.mein.auth.data;

public class ServiceDetails extends ServicePayload {
    private String serviceUuid;
    public ServiceDetails(){

    }

    public ServiceDetails(String serviceUuid) {
        this.serviceUuid = serviceUuid;
    }

    public void setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }
}

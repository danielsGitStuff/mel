package de.mein.auth.data;

import de.mein.auth.data.IPayload;

public class ServiceDetails implements IPayload {
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

package de.mel.auth.data;

import de.mel.core.serialize.SerializableEntity;

public class ClientData implements SerializableEntity {
    private String serviceUuid;
    private Long certId;

    public ClientData(){}

    public ClientData(Long certId, String serviceUuid) {
        this.certId = certId;
        this.serviceUuid = serviceUuid;
    }

    public ClientData setCertId(Long certId) {
        this.certId = certId;
        return this;
    }

    public ClientData setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        return this;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

    public Long getCertId() {
        return certId;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        if (certId != null)
            hash += certId.hashCode();
        if (serviceUuid != null)
            hash += serviceUuid.hashCode();
        if (hash != 0)
            return hash;
        return super.hashCode();
    }
}

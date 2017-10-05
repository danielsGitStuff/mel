package de.mein.contacts.data;

import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsClientSettings implements SerializableEntity{
    private Long serverCertId;
    private String serviceUuid;

    public void setServerCertId(Long serverCertId) {
        this.serverCertId = serverCertId;
    }

    public void setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
    }

    public Long getServerCertId() {
        return serverCertId;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }
}

package de.mel.contacts.data;

import de.mel.core.serialize.SerializableEntity;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsClientSettings implements SerializableEntity{
    private Long serverCertId;
    private String serviceUuid;
    private Long lastReadId;

    /**
     * if set to false the initialization needs to be done when booting (pair with the server service)
     */
    private Boolean initFinished = false;

    public ContactsClientSettings setInitFinished(Boolean initFinished) {
        this.initFinished = initFinished;
        return this;
    }

    public Boolean getInitFinished() {
        return initFinished;
    }

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

    public Long getLastReadId() {
        return lastReadId;
    }

    public void setLastReadId(Long lastReadId) {
        this.lastReadId = lastReadId;
    }


}

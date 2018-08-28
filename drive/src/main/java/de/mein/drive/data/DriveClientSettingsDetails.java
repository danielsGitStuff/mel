package de.mein.drive.data;

import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 10/26/16.
 */
public class DriveClientSettingsDetails implements SerializableEntity {
    private Long serverCertId;
    private String serverServiceUuid;

    public DriveClientSettingsDetails setServerCertId(Long serverCertId) {
        this.serverCertId = serverCertId;
        return this;
    }

    public DriveClientSettingsDetails setServerServiceUuid(String serverServiceUuid) {
        this.serverServiceUuid = serverServiceUuid;
        return this;
    }

    public Long getServerCertId() {
        return serverCertId;
    }

    public String getServerServiceUuid() {
        return serverServiceUuid;
    }
}

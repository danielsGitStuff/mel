package de.mel.auth.data;

import de.mel.core.serialize.SerializableEntity;

public class VersionInfo implements SerializableEntity {
    private Long version;


    public Long getVersion() {
        return version;
    }

    public VersionInfo setVersion(Long version) {
        this.version = version;
        return this;
    }
}

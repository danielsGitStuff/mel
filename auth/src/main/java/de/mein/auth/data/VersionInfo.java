package de.mein.auth.data;

import de.mein.core.serialize.SerializableEntity;

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

package de.miniserver.data;

import de.mein.core.serialize.SerializableEntity;

public class VersionAnswer implements SerializableEntity {
    private Long androidVersion, pcVersion;
    private String androidSHA256, pcSHA256;

    public VersionAnswer setAndroidVersion(Long androidVersion) {
        this.androidVersion = androidVersion;
        return this;
    }

    public VersionAnswer setAndroidSHA256(String androidSHA256) {
        this.androidSHA256 = androidSHA256;
        return this;
    }

    public VersionAnswer setPcSHA256(String pcSHA256) {
        this.pcSHA256 = pcSHA256;
        return this;
    }

    public VersionAnswer setPcVersion(Long pcVersion) {
        this.pcVersion = pcVersion;
        return this;
    }
}

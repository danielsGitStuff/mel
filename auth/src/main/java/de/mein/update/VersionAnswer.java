package de.mein.update;

import de.mein.core.serialize.SerializableEntity;

import java.util.HashMap;
import java.util.Map;

public class VersionAnswer implements SerializableEntity {

    public void addEntry(String hash, String variant, Long version, Long length) {
        entries.put(variant, new VersionEntry().setHash(hash).setVersion(version).setLength(length));
    }

    public String getHash(String variant) {
        if (entries.containsKey(variant))
            return entries.get(variant).getHash();
        return null;
    }

    public VersionEntry getEntry(String variant) {
        return entries.get(variant);
    }

    public static class VersionEntry implements SerializableEntity {
        private Long version, length;
        private String hash;

        public VersionEntry setHash(String hash) {
            this.hash = hash;
            return this;
        }

        public VersionEntry setLength(Long length) {
            this.length = length;
            return this;
        }

        public Long getLength() {
            return length;
        }

        public String getHash() {
            return hash;
        }

        public VersionEntry setVersion(Long version) {
            this.version = version;
            return this;
        }


        public Long getVersion() {
            return version;
        }
    }

    private Map<String, VersionEntry> entries = new HashMap<>();


}

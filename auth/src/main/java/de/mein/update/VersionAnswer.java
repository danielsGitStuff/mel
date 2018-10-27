package de.mein.update;

import de.mein.core.serialize.SerializableEntity;

import java.util.HashMap;
import java.util.Map;

public class VersionAnswer implements SerializableEntity {

    public void addEntry(String hash, String variant, String version) {
        entries.put(variant, new VersionEntry().setHash(hash).setVersion(version));
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
        private String version, hash;

        public VersionEntry setHash(String hash) {
            this.hash = hash;
            return this;
        }

        public String getHash() {
            return hash;
        }

        public VersionEntry setVersion(String version) {
            this.version = version;
            return this;
        }


        public String getVersion() {
            return version;
        }
    }

    private Map<String, VersionEntry> entries = new HashMap<>();


}

package de.mel.update;

import de.mel.core.serialize.SerializableEntity;

import java.util.HashMap;
import java.util.Map;

public class VersionAnswer implements SerializableEntity {

    public void addEntry(String hash, String variant, String commit, String version, Long length) {
        entries.put(variant, new VersionEntry().setHash(hash).setVersion(version).setLength(length).setCommit(commit));
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
        private Long length;
        private String hash, commit, version;

        public VersionEntry setCommit(String commit) {
            this.commit = commit;
            return this;
        }

        public String getCommit() {
            return commit;
        }

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

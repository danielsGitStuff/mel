package de.mel.filesync.data;

import java.util.HashSet;
import java.util.Set;

import de.mel.auth.data.ServicePayload;

public class AvailableHashes extends ServicePayload {
    private Set<String> hashes = new HashSet<>();

    public synchronized AvailableHashes addHash(String hash) {
        hashes.add(hash);
        return this;
    }

    public Set<String> getHashes() {
        return hashes;
    }

    public void clear() {
        hashes.clear();
    }
}



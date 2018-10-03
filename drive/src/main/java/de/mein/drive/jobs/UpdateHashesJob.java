package de.mein.drive.jobs;

import java.util.Set;

import de.mein.auth.jobs.Job;

public class UpdateHashesJob extends Job {
    private final Set<String> availableHashes;

    public UpdateHashesJob(Set<String> availableHashes) {
        this.availableHashes = availableHashes;
    }

    public Set<String> getAvailableHashes() {
        return availableHashes;
    }
}

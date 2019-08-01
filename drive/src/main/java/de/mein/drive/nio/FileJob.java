package de.mein.drive.nio;

import de.mein.auth.jobs.Job;

public class FileJob extends Job {
    private FileDistributionTask distributionTask;

    public FileJob setDistributionTask(FileDistributionTask distributionTask) {
        this.distributionTask = distributionTask;
        return this;
    }

    public FileDistributionTask getDistributionTask() {
        return distributionTask;
    }
}

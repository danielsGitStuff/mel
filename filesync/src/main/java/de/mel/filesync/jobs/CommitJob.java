package de.mel.filesync.jobs;

import de.mel.auth.jobs.Job;

/**
 * Created by xor on 4/24/17.
 */
public class CommitJob extends Job {

    private boolean syncAnyway = false;

    public CommitJob(boolean syncAnyway) {
        this.syncAnyway = syncAnyway;
    }

    public CommitJob() {
    }

    public boolean getSyncAnyway() {
        return syncAnyway;
    }
}

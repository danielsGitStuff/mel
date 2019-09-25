package de.mel.drive.jobs;

import de.mel.Lok;
import de.mel.auth.jobs.Job;
import de.mel.auth.tools.Eva;

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

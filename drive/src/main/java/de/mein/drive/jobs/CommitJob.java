package de.mein.drive.jobs;

import de.mein.Lok;
import de.mein.auth.jobs.Job;
import de.mein.auth.tools.Eva;

/**
 * Created by xor on 4/24/17.
 */
public class CommitJob extends Job {

    private boolean syncAnyway = false;

    public CommitJob(boolean syncAnyway) {
        this.syncAnyway = syncAnyway;
    }

    public CommitJob() {
        Lok.debug("CommitJob.CommitJob");
        Eva.flag("cj");
        if (Eva.getFlagCount("cj") == 3) {
            Lok.debug();
        }
        Lok.debug("eva cj=" + Eva.getFlagCount("cj"));
    }

    public boolean getSyncAnyway() {
        return syncAnyway;
    }
}

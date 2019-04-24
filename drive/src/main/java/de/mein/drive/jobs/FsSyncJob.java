package de.mein.drive.jobs;

import de.mein.auth.jobs.Job;
import de.mein.drive.data.PathCollection;

/**
 * Created by xor on 10/21/16.
 */
public class FsSyncJob extends Job  {

    private final PathCollection pathCollection;

    public FsSyncJob(PathCollection pathCollection) {
        this.pathCollection=pathCollection;
    }

    public PathCollection getPathCollection() {
        return pathCollection;
    }
}

package de.mel.drive.jobs;

import de.mel.Lok;
import de.mel.auth.jobs.Job;

/**
 * Created by xor on 5/25/17.
 */
public class SyncClientJob extends Job {
    private Long newVersion;

    public SyncClientJob(){
        Lok.debug("SyncClientJob.SyncClientJob");
    }

    public SyncClientJob(Long newVersion) {
        this.newVersion=newVersion;
    }

    public Long getNewVersion() {
        return newVersion;
    }
}

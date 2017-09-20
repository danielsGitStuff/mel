package de.mein.drive.jobs;

import de.mein.auth.jobs.Job;

/**
 * Created by xor on 5/25/17.
 */
public class SyncClientJob extends Job {
    private Long newVersion;

    public SyncClientJob(){
        System.out.println("SyncClientJob.SyncClientJob");
    }

    public SyncClientJob(Long newVersion) {
        this.newVersion=newVersion;
    }

    public Long getNewVersion() {
        return newVersion;
    }
}

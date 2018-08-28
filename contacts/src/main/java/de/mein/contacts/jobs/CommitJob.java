package de.mein.contacts.jobs;

import de.mein.auth.jobs.Job;

/**
 * Created by xor on 10/24/17.
 */

public class CommitJob extends Job {
    private Long phoneBookId;

    public Long getPhoneBookId() {
        return phoneBookId;
    }

    public void setPhoneBookId(Long phoneBookId) {
        this.phoneBookId = phoneBookId;
    }

    public CommitJob() {

    }

    public CommitJob(Long phoneBookId) {
        this.phoneBookId = phoneBookId;
    }
}

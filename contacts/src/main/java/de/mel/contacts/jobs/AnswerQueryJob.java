package de.mel.contacts.jobs;

import de.mel.auth.jobs.Job;
import de.mel.auth.socket.process.val.Request;

/**
 * Created by xor on 10/4/17.
 */

public class AnswerQueryJob extends Job {
    private final Request request;

    public AnswerQueryJob(Request request) {
        this.request = request;
    }

    public Request getRequest() {
        return request;
    }
}

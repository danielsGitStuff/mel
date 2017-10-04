package mein.de.contacts.jobs;

import de.mein.auth.jobs.Job;
import de.mein.auth.socket.process.val.Request;

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

package de.mein.auth.jobs;

import org.jdeferred.impl.DeferredObject;

/**
 * Created by xor on 11/13/16.
 */
public class ReceivedJob extends  Job {
    private String message;

    public String getMessage() {
        return message;
    }

    public ReceivedJob setMessage(String message) {
        this.message = message;
        return this;
    }
}

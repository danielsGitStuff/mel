package de.mel.contacts.jobs;

import de.mel.Lok;
import de.mel.auth.jobs.Job;

/**
 * Created by xor on 10/4/17.
 * gets received PhonebookId when Promise is resolved
 */

public class QueryJob extends Job<Long,Void,Void> {
    public QueryJob(){
        Lok.debug("QueryJob.QueryJob");
    }
}

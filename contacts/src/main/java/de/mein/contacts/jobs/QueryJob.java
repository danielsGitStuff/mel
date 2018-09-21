package de.mein.contacts.jobs;

import de.mein.Lok;
import de.mein.auth.jobs.Job;

/**
 * Created by xor on 10/4/17.
 * gets received PhonebookId when Promise is resolved
 */

public class QueryJob extends Job<Long,Void,Void> {
    public QueryJob(){
        Lok.debug("QueryJob.QueryJob");
    }
}

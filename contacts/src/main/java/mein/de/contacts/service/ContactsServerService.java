package mein.de.contacts.service;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsServerService extends ContactsService {


    public ContactsServerService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid) {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid);
    }

    @Override
    protected void workWork(Job job) throws Exception {

    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newSingleThreadExecutor();
    }
}

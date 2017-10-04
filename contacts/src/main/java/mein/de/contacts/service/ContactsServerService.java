package mein.de.contacts.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.ContactStrings;
import mein.de.contacts.data.ContactsSettings;
import mein.de.contacts.jobs.AnswerQueryJob;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsServerService extends ContactsService {


    public ContactsServerService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
    }

    @Override
    protected void workWork(Job job) throws Exception {
        System.out.println("ContactsServerService.workWork.nothing here yet");
    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newSingleThreadExecutor();
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        System.out.println(getClass().getSimpleName() + ".handleRequest");
        if (request.getIntent().equals(ContactStrings.INTENT_QUERY)) {
            addJob(new AnswerQueryJob(request));
        }
    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {

    }
}

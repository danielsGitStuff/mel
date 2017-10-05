package de.mein.contacts.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import de.mein.contacts.data.ContactsSettings;

/**
 * Created by xor on 10/4/17.
 */

public class ContactsClientService extends ContactsService {

    public ContactsClientService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
    }

    @Override
    public void onContactsChanged() {

    }

    @Override
    public void handleRequest(Request request) throws Exception {

    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {

    }

    @Override
    protected void workWork(Job job) throws Exception {

    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return null;
    }
}

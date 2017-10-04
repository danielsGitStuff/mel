package de.mein.android.contacts;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.ContactsSettings;
import mein.de.contacts.data.db.Contact;
import mein.de.contacts.jobs.ExamineJob;
import mein.de.contacts.service.ContactsClientService;

/**
 * Created by xor on 10/4/17.
 */

public class AndroidContactsClientService extends ContactsClientService {

    private final AndroidServiceMethods serviceMethods;

    public AndroidContactsClientService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
        serviceMethods = new AndroidServiceMethods(databaseManager);
        addJob(new ExamineJob());
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        super.handleRequest(request);
    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {
        super.handleMessage(payload, partnerCertificate, intent);
    }

    @Override
    protected void workWork(Job job) throws Exception {
        if (job instanceof ExamineJob) {
                List<Contact> changedContacts = serviceMethods.examineContacts(false);
        }
        super.workWork(job);
    }
}

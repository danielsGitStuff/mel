package de.mein.contacts.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import de.mein.Lok;
import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.NewVersionDetails;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.jobs.ExamineJob;
import de.mein.contacts.jobs.QueryJob;
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
    public void connectionAuthenticated(Certificate partnerCertificate) {
        if (partnerCertificate.getId().equalsValue(settings.getClientSettings().getServerCertId())){
            addJob(new ExamineJob());
        }else {
            Lok.debug("received a cert id that is not from server: "+partnerCertificate.getId().v());
        }
    }

    @Override
    public void handleRequest(Request request) throws Exception {

    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {
        if (intent != null && intent.equals(ContactStrings.INTENT_PROPAGATE_NEW_VERSION)) {
            NewVersionDetails newVersionDetails = (NewVersionDetails) payload;
            try {
                PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
                if (masterPhoneBook == null
                        || masterPhoneBook.getVersion().notEqualsValue(newVersionDetails.getVersion())) {
                    addJob(new QueryJob());
                }
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void workWork(Job job) throws Exception {

    }


    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return null;
    }
}

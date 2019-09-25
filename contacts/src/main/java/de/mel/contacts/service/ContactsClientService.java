package de.mel.contacts.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import de.mel.Lok;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.Job;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.val.Request;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.data.NewVersionDetails;
import de.mel.contacts.data.db.PhoneBook;
import de.mel.contacts.jobs.ExamineJob;
import de.mel.contacts.jobs.QueryJob;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.sql.SqlQueriesException;
import de.mel.contacts.data.ContactsSettings;

/**
 * Created by xor on 10/4/17.
 */

public class ContactsClientService extends ContactsService {


    public ContactsClientService(MelAuthService melAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
    }

    @Override
    public void onContactsChanged() {
        Lok.debug("contacts changed");
    }

    @Override
    public void connectionAuthenticated(Certificate partnerCertificate) {
        if (partnerCertificate.getId().equalsValue(settings.getClientSettings().getServerCertId())) {
            addJob(new ExamineJob());
        } else {
            Lok.debug("received a cert id that is not from server: " + partnerCertificate.getId().v());
        }
    }

    @Override
    public void onBootLevel2Finished() {

    }

    @Override
    public void onBootLevel1Finished() {

    }

    @Override
    public void handleRequest(Request request) throws Exception {

    }

    @Override
    public void handleMessage(ServicePayload payload, Certificate partnerCertificate) {
        String intent = payload.getIntent();
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

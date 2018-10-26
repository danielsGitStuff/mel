package de.mein.contacts.service;

import de.mein.Lok;
import de.mein.auth.data.ClientData;
import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceRequestHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.NewVersionDetails;
import de.mein.contacts.data.ServiceDetails;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.contacts.jobs.AnswerQueryJob;
import de.mein.contacts.jobs.UpdatePhoneBookJob;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsServerService extends ContactsService {


    public ContactsServerService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
    }

    @Override
    public void onContactsChanged() {

    }

    @Override
    protected void workWork(Job job) throws Exception {
        Lok.debug("ContactsServerService.workWork.nothing here yet");
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        ContactsSettings settings = databaseManager.getSettings();
        if (job instanceof AnswerQueryJob) {
            AnswerQueryJob answerQueryJob = (AnswerQueryJob) job;
            PhoneBook phoneBook = databaseManager.getPhoneBookDao().loadDeepPhoneBook(databaseManager.getSettings().getMasterPhoneBookId());
            answerQueryJob.getRequest().resolve(phoneBook);
        } else if (job instanceof UpdatePhoneBookJob) {
            try {
                UpdatePhoneBookJob updatePhoneBookJob = (UpdatePhoneBookJob) job;
                PhoneBook phoneBook = updatePhoneBookJob.getPhoneBook();
                phoneBook.getOriginal().v(false);
                PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
                if (masterPhoneBook == null
                        || phoneBook.getVersion().v() == masterPhoneBook.getVersion().v() + 1) {
                    phoneBookDao.insertDeep(phoneBook);
                    settings.setMasterPhoneBookId(phoneBook.getId().v());
                    settings.save();
                    updatePhoneBookJob.getRequest().resolve(null);
                    updatePhoneBookJob.getPromise().resolve(null);
                    propagateNewVersion(phoneBook.getVersion().v());
                } else {
                    updatePhoneBookJob.getRequest().reject(new Exception("master version was " + masterPhoneBook.getVersion().v() + " clients version was " + phoneBook.getVersion().v()));
                    updatePhoneBookJob.getPromise().reject(null);
                }
            } finally {
                if (job.getPromise().isPending())
                    job.getPromise().reject(null);
            }
        } else if (job instanceof ServiceRequestHandlerJob) {
            ServiceRequestHandlerJob messageHandlerJob = (ServiceRequestHandlerJob) job;
            if (messageHandlerJob.getRequest().hasIntent(ContactStrings.INTENT_REG_AS_CLIENT)) {
                ServiceDetails serviceDetails = (ServiceDetails) messageHandlerJob.getRequest().getPayload();
                settings.getServerSettings().addClient(messageHandlerJob.getPartnerCertificate().getId().v(), serviceDetails.getServiceUuid());
                settings.save();
                messageHandlerJob.getRequest().resolve(null);
            } else {
                messageHandlerJob.getRequest().reject(null);
            }
        }
    }

    protected void propagateNewVersion(Long version) {
        try {
            for (ClientData client : settings.getServerSettings().getClients()) {
                meinAuthService.connect(client.getCertId()).done(mvp ->
                        N.r(() -> mvp.message(client.getServiceUuid(), ContactStrings.INTENT_PROPAGATE_NEW_VERSION, new NewVersionDetails(version))));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newSingleThreadExecutor();
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        Lok.debug(getClass().getSimpleName() + ".handleRequest");
        if (request.hasIntent(ContactStrings.INTENT_QUERY)) {
            addJob(new AnswerQueryJob(request));
        } else if (request.hasIntent(ContactStrings.INTENT_UPDATE)) {
            addJob(new UpdatePhoneBookJob(request));
        } else if (request.hasIntent(ContactStrings.INTENT_REG_AS_CLIENT))
            addJob(new ServiceRequestHandlerJob().setRequest(request));
    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {
        addJob(new ServiceRequestHandlerJob().setPayload(payload).setPartnerCertificate(partnerCertificate).setIntent(intent));
    }
}

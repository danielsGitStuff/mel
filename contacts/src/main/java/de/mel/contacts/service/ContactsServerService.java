package de.mel.contacts.service;

import de.mel.Lok;
import de.mel.auth.data.ClientData;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.Job;
import de.mel.auth.jobs.ServiceRequestHandlerJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.data.ContactsSettings;
import de.mel.contacts.data.NewVersionDetails;
import de.mel.auth.data.ServiceDetails;
import de.mel.contacts.data.db.PhoneBook;
import de.mel.contacts.data.db.PhoneBookWrapper;
import de.mel.contacts.data.db.dao.PhoneBookDao;
import de.mel.contacts.jobs.AnswerQueryJob;
import de.mel.contacts.jobs.UpdatePhoneBookJob;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.sql.SqlQueriesException;

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


    public ContactsServerService(MelAuthService melAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
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
            PhoneBookWrapper wrapper = new PhoneBookWrapper(phoneBook);
            answerQueryJob.getRequest().resolve(wrapper);
        } else if (job instanceof UpdatePhoneBookJob) {
            UpdatePhoneBookJob updatePhoneBookJob = (UpdatePhoneBookJob) job;
            try {
                PhoneBook phoneBook = updatePhoneBookJob.getPhoneBook();
                phoneBook.getOriginal().v(false);
                PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
                if (masterPhoneBook == null
                        || phoneBook.getVersion().v() == masterPhoneBook.getVersion().v() + 1) {
                    phoneBookDao.insertDeep(phoneBook);
                    settings.setMasterPhoneBookId(phoneBook.getId().v());
                    settings.save();
                    updatePhoneBookJob.getRequest().resolve(null);
                    updatePhoneBookJob.resolve(null);
                    propagateNewVersion(phoneBook.getVersion().v());
                } else {
                    updatePhoneBookJob.getRequest().reject(new Exception("master version was " + masterPhoneBook.getVersion().v() + " clients version was " + phoneBook.getVersion().v()));
                    updatePhoneBookJob.reject(null);
                }
            } finally {
                if (updatePhoneBookJob.getRequest().isPending()) {
                    updatePhoneBookJob.getRequest().reject(null);
                    updatePhoneBookJob.reject(null);
                }
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
                melAuthService.connect(client.getCertId()).done(mvp ->
                        N.r(() -> mvp.message(client.getServiceUuid(), new NewVersionDetails(version, ContactStrings.INTENT_PROPAGATE_NEW_VERSION))));
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
    public void handleMessage(ServicePayload payload, Certificate partnerCertificate) {
        addJob(new ServiceRequestHandlerJob().setPayload(payload).setPartnerCertificate(partnerCertificate).setIntent(payload.getIntent()));
    }
}

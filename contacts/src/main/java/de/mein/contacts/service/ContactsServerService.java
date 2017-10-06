package de.mein.contacts.service;

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
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.contacts.jobs.ExamineJob;
import de.mein.contacts.jobs.UpdatePhoneBookJob;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.jobs.AnswerQueryJob;

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
        System.out.println("ContactsServerService.workWork.nothing here yet");
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        ContactsSettings settings = databaseManager.getSettings();
        if (job instanceof AnswerQueryJob) {
            AnswerQueryJob answerQueryJob = (AnswerQueryJob) job;
            PhoneBook phoneBook = databaseManager.getPhoneBookDao().loadPhoneBook(databaseManager.getSettings().getMasterPhoneBookId());
            answerQueryJob.getRequest().resolve(phoneBook);
        } else if (job instanceof UpdatePhoneBookJob) {
            try {
                UpdatePhoneBookJob updatePhoneBookJob = (UpdatePhoneBookJob) job;
                PhoneBook phoneBook = updatePhoneBookJob.getPhoneBook();
                PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
                if (phoneBook.getVersion().v() == masterPhoneBook.getVersion().v() + 1) {
                    phoneBookDao.insertDeep(phoneBook);
                    settings.setMasterPhoneBookId(phoneBook.getId().v());
                    settings.save();
                    updatePhoneBookJob.getRequest().resolve(null);
                    updatePhoneBookJob.getPromise().resolve(null);
                } else {
                    updatePhoneBookJob.getRequest().reject(new Exception("master version was " + masterPhoneBook.getVersion().v() + " clients version was " + phoneBook.getVersion().v()));
                    updatePhoneBookJob.getPromise().reject(null);
                }
            } finally {
                if (!job.getPromise().isPending())
                    job.getPromise().reject(null);
            }
        }
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

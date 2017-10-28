package de.mein.contacts.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ServiceRequestHandlerJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.CountdownLock;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsClientSettings;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.jobs.CommitJob;
import de.mein.contacts.jobs.QueryJob;
import de.mein.contacts.jobs.UpdatePhoneBookJob;
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
        if (intent!= null && intent.equals(ContactStrings.INTENT_PROPAGATE_NEW_VERSION)){
            addJob(new QueryJob());
        }
    }

    @Override
    protected void workWork(Job job) throws Exception {
        if (job instanceof QueryJob) {
            QueryJob queryJob = (QueryJob) job;
            WaitLock waitLock = new WaitLock().lock();
            final Long serverCertId = databaseManager.getSettings().getClientSettings().getServerCertId();
            final String serviceUuid = databaseManager.getSettings().getClientSettings().getServiceUuid();
            meinAuthService.connect(serverCertId).done(mvp -> N.r(() -> {
                mvp.request(serviceUuid, ContactStrings.INTENT_QUERY, null).done(result -> N.r(() -> {
                    System.out.println("ContactsClientService.workWork.query.success");
                    PhoneBook receivedPhoneBook = (PhoneBook) result;
                    PhoneBook master = databaseManager.getFlatMasterPhoneBook();
                    databaseManager.getPhoneBookDao().insertDeep(receivedPhoneBook);
                    databaseManager.getSettings().setMasterPhoneBookId(receivedPhoneBook.getId().v());
                    databaseManager.getSettings().save();
                    queryJob.getPromise().resolve(receivedPhoneBook.getId().v());
                    waitLock.unlock();
                })).fail(result -> {
                    System.err.println("ContactsClientService.workWork");
                    queryJob.getPromise().reject(null);
                    waitLock.unlock();
                }).always((state, resolved, rejected) -> waitLock.unlock());
            })).fail(result -> {
                System.err.println("ContactsClientService.workWork.query.fail");
                queryJob.getPromise().reject(null);
                waitLock.unlock();
            });
            waitLock.lock();
        }
    }


    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return null;
    }
}

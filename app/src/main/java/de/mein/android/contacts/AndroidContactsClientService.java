package de.mein.android.contacts;

import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import de.mein.android.contacts.data.AndroidContactSettings;
import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.CountLock;
import de.mein.auth.tools.CountdownLock;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsClientSettings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.jobs.ExamineJob;
import de.mein.contacts.jobs.QueryJob;
import de.mein.contacts.service.ContactsClientService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;


/**
 * Created by xor on 10/4/17.
 */

public class AndroidContactsClientService extends ContactsClientService {

    private final AndroidServiceMethods serviceMethods;
    private ContactsToAndroidExporter contactsToAndroidExporter;

    public AndroidContactsClientService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
        serviceMethods = new AndroidServiceMethods(databaseManager);
        AndroidContactSettings androidContactSettings = (AndroidContactSettings) settingsCfg.getPlatformContactSettings();
        if (androidContactSettings.getPersistToPhoneBook()) {
            contactsToAndroidExporter = new ContactsToAndroidExporter(databaseManager);
        }
        databaseManager.maintenance();
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
            PhoneBook phoneBook = serviceMethods.examineContacts();
            PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
            if (masterPhoneBook == null || masterPhoneBook.getHash().notEqualsValue(phoneBook.getHash())) {
                CountdownLock waitLock = new CountdownLock(1).lock();
                ContactsClientSettings clientSettings = databaseManager.getSettings().getClientSettings();
                //phone book has no contacts attached yet
                PhoneBook deepPhoneBook = databaseManager.getPhoneBookDao().loadDeepPhoneBook(phoneBook.getId().v());
                meinAuthService.connect(clientSettings.getServerCertId()).done(meinValidationProcess -> N.r(() -> meinValidationProcess.request(clientSettings.getServiceUuid(), ContactStrings.INTENT_UPDATE, deepPhoneBook)
                        .done(result -> N.r(() -> {
                            System.out.println("AndroidContactsClientService.workWork. update succeeded");
                            databaseManager.getSettings().setMasterPhoneBookId(deepPhoneBook.getId().v());
                            databaseManager.getSettings().save();
                            waitLock.unlock();
                        })).fail(result -> N.r(() -> {
                            System.err.println(getClass().getSimpleName() + " updating server failed!");
                            QueryJob queryJob = new QueryJob();
                            queryJob.getPromise().done(receivedPhoneBookId -> N.r(() -> checkConflict(receivedPhoneBookId)));
                            addJob(queryJob);
                            waitLock.unlock();
                        })))).fail(result -> {
                    System.err.println(getClass().getSimpleName() + " updating server failed!!");
                    waitLock.unlock();
                });
            } else {
                databaseManager.getPhoneBookDao().deletePhoneBook(phoneBook.getId().v());
            }
            waitLock.lock();
        } else {
            super.workWork(job);
        }
    }

    private void checkConflict(Long receivedPhoneBookId) throws SqlQueriesException {
        PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
        if (contactsToAndroidExporter != null) {
            contactsToAndroidExporter.export(receivedPhoneBookId);
        }
    }
}

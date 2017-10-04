package de.mein.android.contacts;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.ContactsContract;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import de.mein.android.Tools;
import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.data.ContactsSettings;
import mein.de.contacts.data.db.PhoneBook;
import mein.de.contacts.data.db.Contact;
import mein.de.contacts.jobs.ExamineJob;
import mein.de.contacts.jobs.AnswerQueryJob;
import mein.de.contacts.service.ContactsServerService;

/**
 * Created by xor on 10/3/17.
 */

public class AndroidContactsServerService extends ContactsServerService {
    private final AndroidServiceMethods serviceMethods;

    public AndroidContactsServerService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, settingsCfg);
        Context context = Tools.getApplicationContext();
        context.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, new ContentObserver(null) {
            @Override
            public boolean deliverSelfNotifications() {
                System.out.println("AndroidContactsServerService.deliverSelfNotifications");
                return super.deliverSelfNotifications();
            }

            @Override
            public void onChange(boolean selfChange) {
                System.out.println("AndroidContactsServerService.onChange");
                super.onChange(selfChange);
                addJob(new ExamineJob());
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                System.out.println("AndroidContactsServerService.onChange");
                super.onChange(selfChange, uri);
            }
        });
        serviceMethods = new AndroidServiceMethods(databaseManager);
        addJob(new ExamineJob());
    }

    @Override
    protected void workWork(Job job) throws Exception {
        System.out.println("AndroidContactsServerService.workWork");
        if (job instanceof ExamineJob) {
            PhoneBook phoneBook = serviceMethods.examineContacts();
            PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
            if (masterPhoneBook == null || masterPhoneBook.getHash().notEqualsValue(phoneBook.getHash())) {
                if (masterPhoneBook == null)
                    phoneBook.getVersion().v(1L);
                else {
                    phoneBook.getVersion().v(masterPhoneBook.getVersion().v() + 1);
                }
                databaseManager.getPhoneBookDao().updateFlat(phoneBook);
                databaseManager.getSettings().setMasterPhoneBookId(phoneBook.getId().v());
                databaseManager.getSettings().save();
            }
        } else if (job instanceof AnswerQueryJob) {
            AnswerQueryJob answerQueryJob = (AnswerQueryJob) job;
            PhoneBook phoneBook = databaseManager.getPhoneBookDao().loadPhoneBook(databaseManager.getSettings().getMasterPhoneBookId());
            answerQueryJob.getRequest().resolve(phoneBook);
        }
    }

    @Override
    public void handleRequest(Request request) throws Exception {

    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {

    }

    @Override
    public void onMeinAuthIsUp() {
        super.onMeinAuthIsUp();
    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newSingleThreadExecutor(threadFactory);
    }


}

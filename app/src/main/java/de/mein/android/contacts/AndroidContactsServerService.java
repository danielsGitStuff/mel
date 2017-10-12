package de.mein.android.contacts;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.ContactsContract;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import de.mein.android.Tools;
import de.mein.android.contacts.data.AndroidContactSettings;
import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.contacts.jobs.AnswerQueryJob;
import de.mein.contacts.jobs.ExamineJob;
import de.mein.contacts.jobs.UpdatePhoneBookJob;
import de.mein.contacts.service.ContactsServerService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;


/**
 * Created by xor on 10/3/17.
 */

public class AndroidContactsServerService extends ContactsServerService {
    private final AndroidServiceMethods serviceMethods;
    private ContactsToAndroidExporter contactsToAndroidExporter;

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
                System.out.println("AndroidContactsServerService.onChange." + selfChange);
                super.onChange(selfChange);
                addJob(new ExamineJob());
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                System.out.println("AndroidContactsServerService.onChange.uri: "+uri);
                super.onChange(selfChange, uri);
            }
        });
        serviceMethods = new AndroidServiceMethods(databaseManager);
        AndroidContactSettings androidContactSettings = (AndroidContactSettings) settingsCfg.getPlatformContactSettings();
        if (androidContactSettings.getPersistToPhoneBook()) {
            contactsToAndroidExporter = new ContactsToAndroidExporter(databaseManager);
        }
        databaseManager.maintenance();
        // examine when booted
        addJob(new ExamineJob());
    }

    private int count = 0;

    @Override
    protected void workWork(Job job) throws Exception {
        System.out.println("AndroidContactsServerService.workWork");
        PhoneBookDao phoneBookDao = databaseManager.getPhoneBookDao();
        ContactsSettings settings = databaseManager.getSettings();
        if (job instanceof ExamineJob) {
            PhoneBook phoneBook = serviceMethods.examineContacts();
            PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
            if (masterPhoneBook == null || masterPhoneBook.getHash().notEqualsValue(phoneBook.getHash())) {
                if (masterPhoneBook == null)
                    phoneBook.getVersion().v(1L);
                else {
                    phoneBook.getVersion().v(masterPhoneBook.getVersion().v() + 1);
                }
                phoneBookDao.updateFlat(phoneBook);
                settings.setMasterPhoneBookId(phoneBook.getId().v());
                settings.save();
                //todo debug
                //contactsToAndroidExporter.export(phoneBook.getId().v());
            }
            //todo debug
//            PhoneBook masterPhoneBook = databaseManager.getFlatMasterPhoneBook();
//            if (count < 1)
//                contactsToAndroidExporter.export(masterPhoneBook.getId().v());
//            count++;
        } else if (job instanceof AnswerQueryJob) {
            super.workWork(job);
        } else if (job instanceof UpdatePhoneBookJob) {
            job.getPromise().done(result -> N.r(() -> {
                UpdatePhoneBookJob updatePhoneBookJob = (UpdatePhoneBookJob) job;
                contactsToAndroidExporter.export(updatePhoneBookJob.getPhoneBook().getId().v());
            })).fail(result -> N.r(() -> {
                System.out.println("AndroidContactsServerService.workWork.update failed :(");
            }));
            super.workWork(job);
        } else {
            super.workWork(job);
        }
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

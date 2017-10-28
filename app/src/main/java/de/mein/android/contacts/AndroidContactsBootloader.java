package de.mein.android.contacts;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.view.ViewGroup;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.Notifier;
import de.mein.android.Threadder;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.contacts.controller.RemoteContactsServiceChooserGuiController;
import de.mein.android.contacts.data.AndroidContactSettings;
import de.mein.android.contacts.service.AndroidContactsClientService;
import de.mein.android.contacts.service.AndroidContactsServerService;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.contacts.ContactsBootloader;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.service.ContactsService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 9/21/17.
 */

public class AndroidContactsBootloader extends ContactsBootloader implements AndroidBootLoader {
    @Override
    protected ContactsService createServerInstance(MeinAuthService meinAuthService, File workingDirectory, Long serviceId, String serviceTypeId, ContactsSettings contactsSettings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        AndroidContactsServerService service = new AndroidContactsServerService(meinAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings);
        addExporter(service, contactsSettings);
        return service;
    }

    private void addExporter(ContactsService service, ContactsSettings settings) {
//        if (((AndroidContactSettings) settings.getPlatformContactSettings()).getPersistToPhoneBook()) {
//            service.setContactsToEnvironmentExporter(new ContactsToAndroidExporter(service.getDatabaseManager()));
//        }
    }

    @Override
    protected ContactsService createClientInstance(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String serviceUuid, ContactsSettings settings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        AndroidContactsClientService service = new AndroidContactsClientService(meinAuthService, workingDirectory, serviceTypeId, serviceUuid, settings);
        addExporter(service, settings);
        return service;
    }

    @Override
    public void createService(Activity activity, MeinAuthService meinAuthService, AndroidServiceCreatorGuiController currentController) {

        System.out.println("AndroidContactsBootloader.createService");
        try {
            RemoteContactsServiceChooserGuiController controller = (RemoteContactsServiceChooserGuiController) currentController;
            ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeByName(new ContactsBootloader().getName());
            AndroidContactSettings platformSettings = new AndroidContactSettings().setPersistToPhoneBook(controller.getPersistToPhoneBook());
            ContactsSettings<AndroidContactSettings> contactsSettings = new ContactsSettings<>();
            contactsSettings.setRole(controller.getRole());
            if (contactsSettings.getRole().equals(ContactStrings.ROLE_CLIENT)) {
                contactsSettings.getClientSettings().setServerCertId(controller.getSelectedCertId());
                contactsSettings.getClientSettings().setServiceUuid(controller.getSelectedService().getUuid().v());
            }
            contactsSettings.setPlatformContactSettings(platformSettings);
            Threadder.runNoTryThread(() -> createService(controller.getName(),contactsSettings));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, MeinActivity activity, ViewGroup rootView, IMeinService runningInstance) {
        activity.annoyWithPermissions(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS);
        return new RemoteContactsServiceChooserGuiController(meinAuthService, activity, rootView);
    }


    @Override
    public AndroidServiceCreatorGuiController inflateEmbeddedView(ViewGroup embedded, MeinActivity activity, MeinAuthService meinAuthService, IMeinService runningInstance) {
        activity.annoyWithPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS);
        return new RemoteContactsServiceChooserGuiController(meinAuthService, activity, embedded);
    }

    @Override
    public int getMenuIcon() {
        return R.drawable.icon_share;
    }

    @Override
    public NotificationCompat.Builder createNotificationBuilder(Context context, IMeinService meinService, MeinNotification meinNotification) {
        String intention = meinNotification.getIntention();
        if (intention.equals(ContactStrings.Notifications.INTENTION_CONFLICT)) {
            return new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SOUND);
        }
        return null;
    }

    @Override
    public Class createNotificationActivityClass(IMeinService meinService, MeinNotification meinNotification) {
        String intention = meinNotification.getIntention();
        if (intention.equals(ContactStrings.Notifications.INTENTION_CONFLICT)) {
            return ContactsConflictsPopupActivity.class;
        }
        return null;
    }
}

package de.mein.android.contacts;

import android.Manifest;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.contacts.controller.AndroidContactsCreateGuiController;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import mein.de.contacts.ContactsBootloader;
import mein.de.contacts.data.ContactsSettings;
import mein.de.contacts.service.ContactsService;

/**
 * Created by xor on 9/21/17.
 */

public class AndroidContactsBootloader extends ContactsBootloader implements AndroidBootLoader {
    @Override
    protected ContactsService createServerInstance(MeinAuthService meinAuthService, File workingDirectory, Long serviceId, String serviceTypeId, ContactsSettings contactsSettings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        return new AndroidContactsServerService(meinAuthService, workingDirectory, serviceId, serviceTypeId, contactsSettings);
    }

    @Override
    protected ContactsService createClientInstance(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String serviceUuid, ContactsSettings settings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        return new AndroidContactsClientService(meinAuthService, workingDirectory, serviceTypeId, serviceUuid, settings);
    }

    @Override
    public void createService(Activity activity, MeinAuthService meinAuthService, AndroidServiceCreatorGuiController currentController) {

        System.out.println("AndroidContactsBootloader.createService");
        try {
            AndroidContactsCreateGuiController controller = (AndroidContactsCreateGuiController) currentController;
            ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeByName(new ContactsBootloader().getName());
            ContactsService contactsService = createService(controller.getName(), controller.getRole());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, MeinActivity activity, View rootView, IMeinService runningInstance) {
        activity.annoyWithPermissions(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS);
        return new AndroidContactsCreateGuiController(activity, rootView);
    }


    @Override
    public AndroidServiceCreatorGuiController inflateEmbeddedView(ViewGroup embedded, MeinActivity activity, MeinAuthService meinAuthService, IMeinService runningInstance) {
        View rootView;
        activity.annoyWithPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS);
        rootView = View.inflate(activity, R.layout.embedded_create_contacts, embedded);
        return new AndroidContactsCreateGuiController(activity, rootView);
    }

    @Override
    public int getMenuIcon() {
        return R.drawable.icon_share;
    }
}

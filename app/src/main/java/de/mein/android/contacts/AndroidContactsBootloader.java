package de.mein.android.contacts;

import android.Manifest;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.contacts.controller.AndroidContactsCreateGuiController;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.android.drive.controller.AndroidDriveCreateGuiController;
import de.mein.android.drive.controller.AndroidDriveEditGuiController;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import mein.de.contacts.ContactsBootloader;
import mein.de.contacts.data.ContactsStrings;
import mein.de.contacts.service.ContactsService;

/**
 * Created by xor on 9/21/17.
 */

public class AndroidContactsBootloader extends ContactsBootloader implements AndroidBootLoader {
    @Override
    public void createService(Activity activity, MeinAuthService meinAuthService, AndroidServiceCreatorGuiController currentController) {

        System.out.println("AndroidContactsBootloader.createService");
        try {
            AndroidContactsCreateGuiController controller = (AndroidContactsCreateGuiController) currentController;
            ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeByName(new ContactsBootloader().getName());
            ContactsBootloader bootloader = (ContactsBootloader) meinAuthService.getMeinBoot().getBootLoader(type.getType().v());
            ContactsService contactsService = bootloader.createService(controller.getName(), controller.getRole());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, MeinActivity activity, View rootView, IMeinService runningInstance) {
        return null;
    }

    @Override
    public AndroidServiceCreatorGuiController inflateEmbeddedView(ViewGroup embedded, MeinActivity activity, MeinAuthService meinAuthService, IMeinService runningInstance) {
        View rootView;
        activity.annoyWithPermissions(Manifest.permission.WRITE_CONTACTS);
        rootView = View.inflate(activity, R.layout.embedded_create_contacts, embedded);
        return new AndroidContactsCreateGuiController(activity, rootView);
    }

    @Override
    public int getMenuIcon() {
        return R.drawable.icon_share;
    }
}

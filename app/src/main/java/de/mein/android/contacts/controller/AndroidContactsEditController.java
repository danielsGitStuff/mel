package de.mein.android.contacts.controller;

import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.MeinActivity;
import de.mein.android.contacts.data.AndroidContactSettings;
import de.mein.android.controller.AndroidServiceGuiController;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.service.ContactsService;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveService;

/**
 * Created by xor on 01.11.2017.
 */

public class AndroidContactsEditController extends AndroidServiceGuiController {
    private final AndroidContactSettings androidContactSettings;
    private CheckBox cbStoreToPhoneBook;
    private ContactsService contactsService;
    private TextView lblRole;


    public AndroidContactsEditController(MeinAuthService meinAuthService, MainActivity activity, IMeinService runningInstance, ViewGroup embedded) {
        super(activity, embedded, R.layout.embedded_twice_contacts_edit);
        this.contactsService = (ContactsService) runningInstance;
        androidContactSettings = (AndroidContactSettings) contactsService.getSettings().getPlatformContactSettings();
        cbStoreToPhoneBook.setChecked(androidContactSettings.getPersistToPhoneBook());
        String role;
        if (((ContactsService) runningInstance).getSettings().getRole().equals(ContactStrings.ROLE_SERVER))
            role = "Role: Server";
        else
            role = "ROle: Client";
        lblRole.setText(role);
    }


    @Override
    protected void init() {
        Lok.debug("AndroidContactsEditController.init");
        cbStoreToPhoneBook = rootView.findViewById(R.id.cbStoreToPhoneBook);
        lblRole = rootView.findViewById(R.id.lblRole);
    }

    @Override
    public boolean onOkClicked() {
        androidContactSettings.setPersistToPhoneBook(cbStoreToPhoneBook.isChecked());
        N.r(() -> contactsService.getSettings().save());
        return false;
    }
}

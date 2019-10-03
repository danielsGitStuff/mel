package de.mel.android.contacts.controller;

import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.contacts.data.AndroidContactSettings;
import de.mel.android.controller.AndroidServiceGuiController;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.service.ContactsService;

/**
 * Created by xor on 01.11.2017.
 */

public class AndroidContactsEditController extends AndroidServiceGuiController {
    private final AndroidContactSettings androidContactSettings;
    private CheckBox cbStoreToPhoneBook;
    private ContactsService contactsService;
    private TextView lblRole;


    public AndroidContactsEditController(MelAuthService melAuthService, MainActivity activity, IMelService runningInstance, ViewGroup embedded) {
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

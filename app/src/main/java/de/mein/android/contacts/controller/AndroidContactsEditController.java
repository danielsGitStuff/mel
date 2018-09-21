package de.mein.android.contacts.controller;

import android.view.ViewGroup;
import android.widget.CheckBox;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.contacts.data.AndroidContactSettings;
import de.mein.android.contacts.service.AndroidContactsClientService;
import de.mein.android.contacts.service.AndroidContactsServerService;
import de.mein.android.controller.AndroidServiceGuiController;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.service.ContactsService;

/**
 * Created by xor on 01.11.2017.
 */

public class AndroidContactsEditController extends AndroidServiceGuiController {
    private final AndroidContactSettings androidContactSettings;
    private CheckBox cbStoreToPhoneBook;
    private ContactsService contactsService;

    public AndroidContactsEditController(MeinAuthService meinAuthService, MeinActivity activity, IMeinService runningInstance, ViewGroup embedded) {
        super(activity, embedded, R.layout.embedded_twice_contacts);
        this.contactsService = (ContactsService) runningInstance;
        androidContactSettings = (AndroidContactSettings) contactsService.getSettings().getPlatformContactSettings();
        cbStoreToPhoneBook.setChecked(androidContactSettings.getPersistToPhoneBook());
    }


    @Override
    protected void init() {
        Lok.debug("AndroidContactsEditController.init");
        cbStoreToPhoneBook = rootView.findViewById(R.id.cbStoreToPhoneBook);
    }

    @Override
    public void onOkClicked() {
        androidContactSettings.setPersistToPhoneBook(cbStoreToPhoneBook.isChecked());
        N.r(() -> contactsService.getSettings().save());
    }
}

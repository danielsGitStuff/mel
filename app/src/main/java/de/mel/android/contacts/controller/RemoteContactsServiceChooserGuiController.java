package de.mel.android.contacts.controller;

import android.view.ViewGroup;
import android.widget.CheckBox;

import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.controller.RemoteServiceChooserController;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;
import de.mel.contacts.data.ContactStrings;

/**
 * Created by xor on 9/21/17.
 */

public class RemoteContactsServiceChooserGuiController extends RemoteServiceChooserController {
    private CheckBox cbStoreToPhoneBook;

    public RemoteContactsServiceChooserGuiController(MelAuthService melAuthService, MainActivity activity, ViewGroup viewGroup, Bootloader bootloader) {
        super(melAuthService, activity, viewGroup, R.layout.embedded_twice_contacts, bootloader);
    }


    @Override
    protected void initEmbedded() {
        cbStoreToPhoneBook = rootView.findViewById(R.id.cbStoreToPhoneBook);
    }

    public String getRole() {

        return isServer() ? ContactStrings.ROLE_SERVER : ContactStrings.ROLE_CLIENT;
    }

    public boolean getPersistToPhoneBook() {
        return cbStoreToPhoneBook.isChecked();
    }

    @Override
    public boolean onOkClicked() {

        return true;
    }

    @Override
    public int getPermissionsText() {
        return R.string.permissionContacts;
    }
}

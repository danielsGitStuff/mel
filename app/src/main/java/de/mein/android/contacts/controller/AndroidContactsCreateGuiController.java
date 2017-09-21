package de.mein.android.contacts.controller;

import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.auth.service.MeinAuthService;
import mein.de.contacts.data.ContactsStrings;

/**
 * Created by xor on 9/21/17.
 */

public class AndroidContactsCreateGuiController extends AndroidServiceCreatorGuiController {
    private CheckBox cbStoreToPhoneBook;
    private EditText txtName;

    public AndroidContactsCreateGuiController(MeinActivity activity, View rootView) {
        super(activity, rootView);
    }


    @Override
    protected void init() {
        cbStoreToPhoneBook = rootView.findViewById(R.id.cbStoreToPhoneBook);
        txtName = rootView.findViewById(R.id.txtName);
    }

    public String getName() {
        return txtName.getText().toString();
    }

    public String getRole() {
        return ContactsStrings.ROLE_SERVER;
    }
}

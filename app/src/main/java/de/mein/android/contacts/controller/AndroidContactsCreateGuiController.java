package de.mein.android.contacts.controller;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.android.controller.ChooseServerServiceController;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.contacts.data.ContactStrings;

/**
 * Created by xor on 9/21/17.
 */

public class AndroidContactsCreateGuiController extends ChooseServerServiceController {
    private CheckBox cbStoreToPhoneBook;
    private EditText txtName;

    public AndroidContactsCreateGuiController(MeinAuthService meinAuthService, MeinActivity activity, ViewGroup viewGroup) {
        super(meinAuthService, activity, viewGroup,R.layout.embedded_twice_contacts);
    }


    @Override
    protected void initEmbedded() {

    }

    @Override
    protected void init() {
        cbStoreToPhoneBook = rootView.findViewById(R.id.cbStoreToPhoneBook);
        txtName = rootView.findViewById(R.id.txtName);
    }

    @Override
    protected boolean showService(ServiceJoinServiceType service) {
        return service.getType().equalsValue(ContactStrings.NAME);
    }

    public String getName() {
        return txtName.getText().toString();
    }

    public String getRole() {
        return ContactStrings.ROLE_SERVER;
    }
}

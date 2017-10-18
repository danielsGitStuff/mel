package de.mein.android.contacts;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MenuItem;

import de.mein.R;
import de.mein.android.ConflictsPopupActivity;
import de.mein.android.PopupActivity;
import de.mein.android.contacts.data.ConflictIntentExtra;
import de.mein.android.contacts.service.AndroidContactsClientService;
import de.mein.android.service.AndroidService;
import de.mein.auth.MeinStrings;
import de.mein.auth.tools.N;
import de.mein.contacts.data.ContactStrings;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;

/**
 * Created by xor on 10/18/17.
 */

public class ContactsConflictsPopupActivity extends ConflictsPopupActivity<AndroidContactsClientService> {
    private Long localPhoneBookId, receivedPhoneBookId;

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        Bundle extras = getIntent().getExtras();
        String json = extras.getString(MeinStrings.Notifications.EXTRA + ContactStrings.Notifications.INTENT_EXTRA_CONFLICT);
        N.r(() -> {
            ConflictIntentExtra conflictIntentExtra = (ConflictIntentExtra) SerializableEntityDeserializer.deserialize(json);
            localPhoneBookId = conflictIntentExtra.getLocalPhoneBookId();
            receivedPhoneBookId = conflictIntentExtra.getReceivedPhoneBookId();
            service.getDatabaseManager().getSettings().getClientSettings().getUncommitedHead();

        });
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}

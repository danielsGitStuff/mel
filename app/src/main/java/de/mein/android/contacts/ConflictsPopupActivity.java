package de.mein.android.contacts;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MenuItem;

import de.mein.android.PopupActivity;
import de.mein.android.contacts.service.AndroidContactsClientService;
import de.mein.android.service.AndroidService;

/**
 * Created by xor on 10/18/17.
 */

public class ConflictsPopupActivity extends PopupActivity<AndroidContactsClientService> {
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}

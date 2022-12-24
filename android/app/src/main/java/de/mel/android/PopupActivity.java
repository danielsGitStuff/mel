package de.mel.android;

import android.content.Context;
import android.os.Bundle;

import java.util.List;

import de.mel.android.service.AndroidService;
import de.mel.auth.MelStrings;
import de.mel.auth.service.IMelService;
import de.mel.auth.tools.N;

public abstract class PopupActivity<T extends IMelService> extends MelActivity {

    protected Integer requestCode;
    protected AndroidService androidService;
    protected String serviceUuid;
    protected T service;
    protected List<MelActivityPayload<?>> payloads;
    protected N runner = new N(e -> {
        Context context = getApplicationContext();
        Notifier.toast(context, e.getMessage());
        System.err.println(PopupActivity.class.getSimpleName() + ".runner.Exception: " + e.getMessage());
        e.printStackTrace();
    });

    protected abstract int layout();

    @Override
    protected void onDestroy() {
        MelActivity.Companion.onLaunchDestroyed(requestCode);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout());
        Bundle extra = getIntent().getExtras();
        if (extra != null) {
            requestCode = extra.getInt(MelStrings.Notifications.REQUEST_CODE);
            serviceUuid = extra.getString(MelStrings.Notifications.SERVICE_UUID);
            payloads = MelActivity.Companion.getLaunchPayloads(requestCode);
        }
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        if (serviceUuid != null)
            service = (T) androidService.getMelAuthService().getMelService(serviceUuid);
    }
}

package de.mein.android;

import android.content.Context;
import android.os.Bundle;

import java.util.List;

import de.mein.android.service.AndroidService;
import de.mein.auth.MeinStrings;
import de.mein.auth.service.IMeinService;
import de.mein.auth.tools.N;

public abstract class PopupActivity<T extends IMeinService> extends MeinActivity {

    protected Integer requestCode;
    protected AndroidService androidService;
    protected String serviceUuid;
    protected T service;
    protected List<MeinActivityPayload> payloads;
    protected N runner = new N(e -> {
        Context context = getApplicationContext();
        Notifier.toast(context, e.getMessage());
        System.err.println(PopupActivity.class.getSimpleName() + ".runner.Exception: " + e.getMessage());
        e.printStackTrace();
    });

    protected abstract int layout();

    @Override
    protected void onDestroy() {
        MeinActivity.onLaunchDestroyed(requestCode);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout());
        Bundle extra = getIntent().getExtras();
        if (extra != null) {
            requestCode = extra.getInt(MeinStrings.Notifications.REQUEST_CODE);
            serviceUuid = extra.getString(MeinStrings.Notifications.SERVICE_UUID);
            payloads = MeinActivity.getLaunchPayloads(requestCode);
        }
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        if (serviceUuid != null)
            service = (T) androidService.getMeinAuthService().getMeinService(serviceUuid);
    }
}

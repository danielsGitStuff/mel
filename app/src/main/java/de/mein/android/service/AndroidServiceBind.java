package de.mein.android.service;

public interface AndroidServiceBind {
    void onAndroidServiceAvailable(AndroidService androidService);

    void onAndroidServiceUnbound(AndroidService androidService);
}

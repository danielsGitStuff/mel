package de.mel.android.service;

public interface AndroidServiceBind {
    void onAndroidServiceAvailable(AndroidService androidService);

    void onAndroidServiceUnbound();
}

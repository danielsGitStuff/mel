package de.mel.android;

import android.app.Application;

public class MelApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Tools.init(this);
    }
}

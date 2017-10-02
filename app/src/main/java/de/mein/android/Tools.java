package de.mein.android;

import android.content.Context;

import java.security.SecureRandom;

/**
 * Created by xor on 9/18/17.
 */

public class Tools {
    private static Context applicationContext;

    public static int generateIntentRequestCode() {
        int value = new SecureRandom().nextInt(65000);
        return value;
    }

    public static void setApplicationContext(Context applicationContext) {
        Tools.applicationContext = applicationContext;
    }

    public static Context getApplicationContext() {
        return applicationContext;
    }
}

package de.mein.android;

import java.security.SecureRandom;

/**
 * Created by xor on 9/18/17.
 */

public class Tools {
    public static int generateIntentRequestCode() {
        int value = new SecureRandom().nextInt(65000);
        return value;
    }
}

package de.mein.android;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by xor on 20.08.2017.
 */

public class MeinToast {
    public static void toast(Context context, String message) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        toast.show();
    }
}

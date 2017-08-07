package de.mein.android.controller;

import de.mein.android.MeinActivity;
import de.mein.auth.service.MeinAuthService;
import de.mein.android.service.AndroidService;

/**
 * Created by xor on 2/23/17.
 */

public abstract class GuiController {

    protected final MeinActivity activity;

    protected GuiController(MeinActivity activity) {
        this.activity = activity;
    }


    public abstract void onMeinAuthStarted(MeinAuthService androidService);

    public abstract void onAndroidServiceBound(AndroidService androidService);

    public abstract void onAndroidServiceUnbound(AndroidService androidService);
}

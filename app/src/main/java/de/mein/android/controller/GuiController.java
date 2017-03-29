package de.mein.android.controller;

import de.mein.auth.service.MeinAuthService;
import de.mein.android.AndroidService;

/**
 * Created by xor on 2/23/17.
 */

public abstract class GuiController {
    public abstract void onMeinAuthStarted(MeinAuthService androidService);

    public abstract void onAndroidServiceBound(AndroidService androidService);

    public abstract void onAndroidServiceUnbound(AndroidService androidService);
}

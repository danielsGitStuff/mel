package de.mein.controller;

import de.mein.auth.service.MeinAuthService;
import de.mein.drive.AndroidService;

/**
 * Created by xor on 2/23/17.
 */

public interface GuiController {
    void onMeinAuthStarted(MeinAuthService androidService);

    void onAndroidServiceBound(AndroidService androidService);
}

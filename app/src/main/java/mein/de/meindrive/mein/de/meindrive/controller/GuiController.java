package mein.de.meindrive.mein.de.meindrive.controller;

import de.mein.auth.service.MeinAuthService;
import mein.de.meindrive.AndroidService;

/**
 * Created by xor on 2/23/17.
 */

public interface GuiController {
    void onMeinAuthStarted(MeinAuthService androidService);

    void onAndroidServiceBound(AndroidService androidService);
}

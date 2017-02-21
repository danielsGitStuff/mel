package de.mein;

import android.view.View;

import de.mein.auth.boot.BootLoader;
import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 2/20/17.
 */

public  interface AndroidBootLoader  {
    View createView(MeinAuthService meinAuthService, View parentView);
}

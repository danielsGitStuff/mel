package de.mein.android.boot;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 2/25/17.
 */

public interface AndroidBootLoader<T extends IMeinService> {

    void createService(Activity activity, MeinAuthService meinAuthService, AndroidServiceCreatorGuiController currentController);

    AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, MeinActivity activity, ViewGroup rootView, IMeinService runningInstance);

    AndroidServiceCreatorGuiController inflateEmbeddedView(ViewGroup embedded, MeinActivity activity, MeinAuthService meinAuthService, IMeinService runningInstance);

    int getMenuIcon();
}

package de.mein.android.boot;

import android.app.Activity;
import android.view.View;

import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.auth.MeinNotification;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinService;

/**
 * Created by xor on 2/25/17.
 */

public interface AndroidBootLoader<T extends IMeinService> {

    Integer getCreateResource();

    Integer getEditResource(T service);

    void createService(Activity activity, MeinAuthService meinAuthService, AndroidServiceCreatorGuiController currentController);

    AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, MeinActivity activity, View rootView);

}

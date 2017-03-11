package de.mein.boot;

import android.app.Activity;
import android.view.View;

import de.mein.AndroidServiceCreatorGuiController;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 2/25/17.
 */

public interface AndroidBootLoader<T extends IMeinService> {

    Integer getCreateResource();

    Integer getEditResource(T service);

    void createService(Activity activity, MeinAuthService meinAuthService);

    AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, Activity activity, View rootView);
}

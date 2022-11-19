package de.mel.android.boot;

import android.app.Activity;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import android.view.ViewGroup;

import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.controller.AndroidServiceGuiController;
import de.mel.auth.MelNotification;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthService;

/**
 * Created by xor on 2/25/17.
 */

public interface AndroidBootLoader<T extends IMelService> {

    void createService(Activity activity, MelAuthService melAuthService, AndroidServiceGuiController currentController);

    AndroidServiceGuiController inflateEmbeddedView(ViewGroup embedded, MainActivity activity, MelAuthService melAuthService, IMelService runningInstance);

    String[] getPermissions();

    int getMenuIcon();

    NotificationCompat.Builder createNotificationBuilder(Context context, IMelService melService, MelNotification melNotification);

    Class createNotificationActivityClass(IMelService melService, MelNotification melNotification);
}
